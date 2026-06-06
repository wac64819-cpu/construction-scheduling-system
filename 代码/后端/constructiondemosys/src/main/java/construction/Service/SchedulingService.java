package construction.Service;

import construction.constraint.*;
import construction.constraint.validator.PrecedenceGraphValidator;
import construction.constraint.validator.TemporalConsistencyChecker;
import construction.dto.*;
import construction.dto.ActivityConstraintDTO;
import construction.dto.LocationPrecedenceConstraintDTO;
import construction.dto.PrecedenceConstraintDTO;
import construction.entity.*;
import construction.model.Activity;
import construction.model.Location;
import construction.model.Task;
import construction.schedule.ParallelScheduler;
import construction.schedule.ScheduleResult;
import construction.schedule.Scheduler;
import construction.problem.ConstructionSchedulingProblem;
import org.uma.jmetal.algorithm.multiobjective.nsgaii.NSGAIIBuilder;
import org.uma.jmetal.util.SolutionListUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import org.uma.jmetal.algorithm.Algorithm;
import org.uma.jmetal.algorithm.examples.AlgorithmRunner;
import org.uma.jmetal.algorithm.multiobjective.nsgaiii.NSGAIIIBuilder;
import org.uma.jmetal.operator.crossover.impl.SBXCrossover;
import org.uma.jmetal.operator.mutation.impl.PolynomialMutation;
import org.uma.jmetal.operator.selection.SelectionOperator;
import org.uma.jmetal.operator.selection.impl.BinaryTournamentSelection;
import org.uma.jmetal.util.comparator.RankingAndCrowdingDistanceComparator;
import org.uma.jmetal.qualityindicator.impl.hypervolume.impl.PISAHypervolume;
import org.uma.jmetal.solution.doublesolution.DoubleSolution;
import org.uma.jmetal.util.evaluator.SolutionListEvaluator;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class SchedulingService {

    @Autowired
    private MongoTemplate mongoTemplate;

    public ScheduleResponse runScheduling(ScheduleRequest request) {

        // ==============================================================
        // ✅ 论文测试专属开关配置区域 (1=True, 0=False)
        // ==============================================================
        
        int testRuns = 1;                         // 独立运行测试的次数 (取平均和极值)
        // ==============================================================

        // -------------------------------------------------------------
        // STEP 1: 加载项目数据
        // -------------------------------------------------------------
        String projectId = request.getProjectId();
        ProjectEntity projectEntity = mongoTemplate.findById(projectId, ProjectEntity.class);
        if (projectEntity == null) {
            throw new RuntimeException("Project not found: " + projectId);
        }

        ProjectSettings settings = projectEntity.settings;
        if (settings == null) settings = new ProjectSettings();

        // -------------------------------------------------------------
        // STEP 2: 构建 Location
        // -------------------------------------------------------------
        Map<String, Location> locationIdMap = new HashMap<>();
        List<Location> allLocations = new ArrayList<>();
        parseLocationTree(projectEntity.model.locationTree, new ArrayList<>(), locationIdMap, allLocations);

        // -------------------------------------------------------------
        // STEP 3: 构建 Tasks
        // -------------------------------------------------------------
        List<Task> tasks = new ArrayList<>();
        for (ActivityDTO actDto : projectEntity.model.activities) {
            Activity activity = new Activity(
                    actDto.name, actDto.trade, actDto.crew, actDto.dailyProd
            );
            if (actDto.quantities != null) {
                for (Map.Entry<String, Double> entry : actDto.quantities.entrySet()) {
                    String locId = entry.getKey();
                    Double qty = entry.getValue();
                    if (qty != null && qty > 0) {
                        Location loc = locationIdMap.get(locId);
                        if (loc != null) tasks.add(new Task(activity, loc, qty));
                    }
                }
            }
        }
        if (tasks.isEmpty()) throw new RuntimeException("No valid tasks found.");

        // -------------------------------------------------------------
        // STEP 4: 加载约束
        // -------------------------------------------------------------
        List<PrecedenceConstraint> precedences = new ArrayList<>();
        List<ActivityConstraint> activityConstraints = new ArrayList<>();
        List<LocationPrecedenceConstraint> locationPrecedences = new ArrayList<>();
        ProjectConstraints pcDto = projectEntity.constraints;

        if (pcDto != null) {
            if (pcDto.precedenceConstraints != null) {
                for (PrecedenceConstraintDTO dto : pcDto.precedenceConstraints) {
                    precedences.add(new PrecedenceConstraint(
                            dto.getPredecessor(), dto.getSuccessor(), dto.getScopeMode(),
                            dto.getScopeLevels(), dto.getDayGap(), dto.getPredType(), dto.getRelation()
                    ));
                }
            }
            if (pcDto.activityConstraints != null) {
                for (ActivityConstraintDTO dto : pcDto.activityConstraints) {
                    activityConstraints.add(new ActivityConstraint(
                            dto.getActivityName(), dto.getType(), dto.getScopeMode(), dto.getScopeLevels()
                    ));
                }
            }
            if (pcDto.locationConstraints != null) {
                for (LocationPrecedenceConstraintDTO dto : pcDto.locationConstraints) {
                    locationPrecedences.add(new LocationPrecedenceConstraint(
                            new LocationPattern(dto.getLoc1Patterns()),
                            new LocationPattern(dto.getLoc2Patterns()),
                            dto.getDayGap(), dto.getPredType(), dto.getApplyActivities(), dto.getRelation()
                    ));
                }
            }
        }

        // -------------------------------------------------------------
        // STEP 5: 加载资源数据
        // -------------------------------------------------------------
        List<TradeData> domainTradeDataList = new ArrayList<>();
        Map<String, Integer> tradeCapacities = new HashMap<>();

        if (projectEntity.tradeData != null) {
            for (TradeDataDTO dto : projectEntity.tradeData) {
                TradeData domainTrade = new TradeData(
                        dto.name,
                        dto.capacity,
                        dto.standardRate
                );
                domainTradeDataList.add(domainTrade);
                tradeCapacities.put(dto.name, dto.capacity);
            }
        }
        try {
            // 提前进行图论校验，拦截死锁与时间倒挂
            PrecedenceGraphValidator.validateDAG(tasks, precedences);
            TemporalConsistencyChecker.validate(tasks, precedences, locationPrecedences);
        } catch (IllegalStateException e) {
            // 捕获到底层 Validator 抛出的 IllegalStateException
            // 转化为 HTTP 400 Bad Request 抛给前端，并附带具体错误信息
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, e.getMessage());
        }
        // -------------------------------------------------------------
        // STEP 6 & 8: 调度器配置与拦截式问题包装 【✅ 修改区：解决返回值冲突】
        // -------------------------------------------------------------
        Scheduler scheduler = new ParallelScheduler();

        int maxEvaluations = (settings.maxEvaluations > 0) ? settings.maxEvaluations : request.getMaxEvaluations();
        int populationSize = (settings.populationSize > 0) ? settings.populationSize : 50;

        int tempMaxIterations = (int) Math.ceil((double) maxEvaluations / populationSize);
        final int finalMaxIterations = settings.maxEvaluations;

        AtomicInteger evalCounter3 = new AtomicInteger(0);
        ConstructionSchedulingProblem problem3 = new ConstructionSchedulingProblem(
                tasks,
                precedences,
                activityConstraints,
                locationPrecedences,
                tradeCapacities,
                settings.maxOutsourcingRatio,
                domainTradeDataList,
                settings,
                scheduler
        ) {
            // ✅ 关键修复：jMetal 6.x 要求 evaluate 必须返回 DoubleSolution
            @Override
            public DoubleSolution evaluate(DoubleSolution solution) {
                evalCounter3.incrementAndGet();
                return super.evaluate(solution);
            }
        };

        double crossoverProb = (settings.crossoverProbability > 0) ? settings.crossoverProbability : 0.9;
        double mutationProb3 = (settings.mutationProbability > 0) ? settings.mutationProbability : (1.0 / problem3.numberOfVariables());
        
        SelectionOperator<List<DoubleSolution>, DoubleSolution> selection = new BinaryTournamentSelection<>(
                new RankingAndCrowdingDistanceComparator<>()
        );



        // -------------------------------------------------------------
        // 生成 Summary (用于前端展示)
        // -------------------------------------------------------------
        List<Location> sortedLocations = tasks.stream()
                .map(Task::getLocation).distinct().sorted().collect(Collectors.toList());

        List<ActivitySummaryDTO> activitySummaries = generateActivitySummaries(tasks);
        List<ConstraintSummaryDTO> constraintSummaries = generateConstraintSummaries(precedences, activityConstraints, locationPrecedences);

        ProjectInfoDTO projectInfoDTO = new ProjectInfoDTO(activitySummaries, constraintSummaries, sortedLocations);

        // ================= 【控制台进度打印开始】 =================
        System.out.println("==========  NSGA-III 调度算法启动 ==========");
        System.out.println(" 项目 ID: " + projectId);
        System.out.println(" 种群大小: " + populationSize);
        System.out.println(" 迭代代数: " + finalMaxIterations + " (总目标评估次数约为: " + (finalMaxIterations * populationSize) + ")");
        System.out.println("⚔️ 交叉概率: " + crossoverProb + " | 变异概率: " + String.format("%.4f", mutationProb3));
        System.out.println("----------------------------------------------");


        AlgoStats nsga3Stats = new AlgoStats();
        

        List<DoubleSolution> finalPareto = null; // 用于后续返回

        for (int run = 1; run <= testRuns; run++) {

            System.out.println("\n================ 第 " + run + " 次实验 ================");
            evalCounter3.set(0);
            
            // =====================================================
            // NSGA-III
            // =====================================================


            Algorithm<List<DoubleSolution>> nsga3 = new NSGAIIIBuilder<>(problem3)
                    .setCrossoverOperator(new SBXCrossover(crossoverProb, 20.0))
                    .setMutationOperator(new PolynomialMutation(mutationProb3, 20.0))
                    .setSelectionOperator(selection)
                    .setPopulationSize(populationSize)
                    .setMaxIterations(finalMaxIterations)
                    .setNumberOfDivisions(5)
                    .build();

            long t1 = System.currentTimeMillis();
            new AlgorithmRunner.Executor(nsga3).execute();
            long t2 = System.currentTimeMillis();

            List<DoubleSolution> res3 = nsga3.result();
            List<DoubleSolution> pareto3 = SolutionListUtils.getNonDominatedSolutions(res3);

            double hv3 = calculateHVForThesis(pareto3);
            double sp3 = calculateSpacingForThesis(pareto3);
            // ✅ 在这里加
            int evals3 = evalCounter3.get();
            System.out.println("NSGA-III Evaluations: " + evals3);


            System.out.println("NSGA-III → Time: " + (t2 - t1) + " ms | HV: " + hv3 + " | SP: " + sp3);

            // 保存最后一次结果用于返回（你原有逻辑）
            finalPareto = pareto3;
            int pfSize3 = pareto3.size();
            nsga3Stats.add(t2 - t1, hv3, sp3,pfSize3);
            System.out.println("NSGA-III Pareto Size: " + pfSize3);
             }



        System.out.println("\n================ 实验统计结果 ================");

// NSGA-III
        System.out.println("【NSGA-III】");
        System.out.println("平均时间: " + nsga3Stats.avgTime() + " ms");
        System.out.println("最优时间: " + nsga3Stats.bestTime() + " ms");
        System.out.println("平均 HV : " + nsga3Stats.avgHV());
        System.out.println("最优 HV : " + nsga3Stats.bestHV());
        System.out.println("平均 SP : " + nsga3Stats.avgSP());
        System.out.println("最优 SP : " + nsga3Stats.bestSP());
        System.out.println("平均 PF Size: " + nsga3Stats.avgPF());
        System.out.println("最大 PF Size: " + nsga3Stats.bestPF());
        System.out.println("最小 PF Size: " + nsga3Stats.minPF());

        // -------------------------------------------------------------
        // STEP 9: 结果转换
        // -------------------------------------------------------------
        List<DoubleSolution> paretoJFront = finalPareto;
        List<SolutionDTO> allSolutions = new ArrayList<>();

        for (DoubleSolution sol : paretoJFront) {
            ScheduleResult result = scheduler.generateSchedule(
                    sol,
                    tasks,
                    precedences,
                    activityConstraints,
                    locationPrecedences,
                    tradeCapacities,
                    settings.maxOutsourcingRatio
            );

            List<TaskScheduleDTO> taskDTOs = tasks.stream()
                    .map(t -> new TaskScheduleDTO(
                            t.getActivity().getName(),
                            t.getLocation().toString(),
                            t.getStartTime(),
                            t.getEndTime()))
                    .collect(Collectors.toList());

            double actualMakespan = result.getMakespan();
            // ✅ 核心修复：废弃 result.getPeakWorkforce()，改用和前端一样的按日统计逻辑
            double actualPeak = calculatePeakLocal(tasks, actualMakespan);
            double actualFluctuation = calculateFluctuationLocal(tasks, actualMakespan);
            double actualContinuity = calculateDiscontinuityLocal(tasks);
            double actualCost = calculateTotalCostLocal(actualMakespan, actualContinuity, tasks, domainTradeDataList, settings);

            allSolutions.add(new SolutionDTO(
                    actualMakespan,
                    actualPeak,
                    actualFluctuation,
                    actualContinuity,
                    actualCost,
                    taskDTOs
            ));
        }

        List<SolutionDTO> paretoFront = filterParetoFront(allSolutions);

        ScheduleResponse response =new ScheduleResponse(paretoFront, projectInfoDTO);
        // ✅ 核心逻辑：保存运行结果到数据库
        projectEntity.latestSchedule = response;
        //mongoTemplate.save(projectEntity);

        return response;
    }

    // =========================================================================
    // 本地计算目标的辅助方法
    // =========================================================================
    // ✅ 新增：按日统计的峰值人力计算
    private double calculatePeakLocal(List<Task> tasks, double makespan) {
        int maxDay = (int) Math.ceil(makespan);
        int[] dailyTotalUsage = new int[maxDay + 2];

        for (Task t : tasks) {
            // 加入 0.001 容差，防止整数被错误进位
            int start = (int) Math.floor(t.getStartTime() + 0.001);
            int end = (int) Math.ceil(t.getEndTime() - 0.001);
            int crew = t.getActivity().getCrewSize();

            for (int day = start; day < end; day++) {
                if (day <= maxDay) {
                    dailyTotalUsage[day] += crew;
                }
            }
        }

        int peak = 0;
        for (int usage : dailyTotalUsage) {
            if (usage > peak) {
                peak = usage;
            }
        }
        return peak;
    }


    private double calculateFluctuationLocal(List<Task> tasks, double makespan) {
        int maxDay = (int) Math.ceil(makespan);
        int[] dailyTotalUsage = new int[maxDay + 2];

        for (Task t : tasks) {
            int start = (int) t.getStartTime();
            int end = (int) t.getEndTime();
            int crew = t.getActivity().getCrewSize();
            for (int day = start; day < end; day++) {
                if (day <= maxDay) {
                    dailyTotalUsage[day] += crew;
                }
            }
        }

        double fluctuation = 0.0;
        for (int i = 0; i < maxDay; i++) {
            double diff = dailyTotalUsage[i] - dailyTotalUsage[i + 1];
            fluctuation += diff * diff;
        }
        return fluctuation;
    }

    private double calculateDiscontinuityLocal(List<Task> tasks) {
        double totalIdleDays = 0.0;
        Map<String, List<Task>> tasksByTrade = tasks.stream()
                .collect(Collectors.groupingBy(t -> t.getActivity().getTrade()));

        for (List<Task> tradeTasks : tasksByTrade.values()) {
            tradeTasks.sort(Comparator.comparingDouble(Task::getStartTime));
            double maxEndTimeSoFar = -1.0;

            for (Task t : tradeTasks) {
                if (maxEndTimeSoFar >= 0) {
                    double gap = t.getStartTime() - maxEndTimeSoFar;
                    if (gap > 0.001) {
                        totalIdleDays += gap;
                    }
                }
                maxEndTimeSoFar = Math.max(maxEndTimeSoFar, t.getEndTime());
            }
        }
        return totalIdleDays;
    }

    private double calculateTotalCostLocal(double makespan, double totalIdleDays, List<Task> tasks, List<TradeData> tradeDataList, ProjectSettings settings) {
        double totalCost = 0.0;

        if (settings != null) {
            totalCost += makespan * settings.indirectCostPerDay;
            totalCost += totalIdleDays * settings.idleCostPerDay;
        }

        int maxDay = (int) Math.ceil(makespan);
        Map<String, int[]> tradeUsageMap = new HashMap<>();

        for (TradeData trade : tradeDataList) {
            tradeUsageMap.put(trade.getTradeName(), new int[maxDay + 1]);
        }

        for (Task t : tasks) {
            String tradeName = t.getActivity().getTrade();
            if (tradeUsageMap.containsKey(tradeName)) {
                int[] usage = tradeUsageMap.get(tradeName);
                int start = (int) t.getStartTime();
                int end = (int) t.getEndTime();
                int crew = t.getActivity().getCrewSize();

                for (int d = start; d < end; d++) {
                    if (d < usage.length) {
                        usage[d] += crew;
                    }
                }
            }
        }

        for (TradeData trade : tradeDataList) {
            int[] dailyUsage = tradeUsageMap.get(trade.getTradeName());
            if (dailyUsage == null) continue;

            for (int used : dailyUsage) {
                if (used <= 0) continue;

                if (used <= trade.getAvailableWorkers()) {
                    totalCost += used * trade.getStandardRate();
                } else {
                    int own = trade.getAvailableWorkers();
                    int outsourced = used - trade.getAvailableWorkers();
                    double multiplier = (settings != null) ? settings.outsourcingCostMultiplier : 1.5;

                    double dailyCost = (own * trade.getStandardRate()) +
                            (outsourced * trade.getStandardRate() * multiplier);

                    totalCost += dailyCost;
                }
            }
        }
        return totalCost;
    }

    // =========================================================================
    // 原有辅助方法
    // =========================================================================

    private void parseLocationTree(List<LocationNode> nodes, List<String> pathNames, Map<String, Location> idMap, List<Location> allList) {
        if (nodes == null) return;
        for (LocationNode node : nodes) {
            List<String> currentPath = new ArrayList<>(pathNames);
            currentPath.add(node.name);
            if (node.children == null || node.children.isEmpty()) {
                Location loc = new Location(currentPath.toArray(new String[0]));
                idMap.put(node.id, loc);
                allList.add(loc);
            } else {
                parseLocationTree(node.children, currentPath, idMap, allList);
            }
        }
    }

    private List<ActivitySummaryDTO> generateActivitySummaries(List<Task> tasks) {
        Map<String, List<Task>> tasksByActivity = tasks.stream().collect(Collectors.groupingBy(t -> t.getActivity().getName()));
        List<ActivitySummaryDTO> list = new ArrayList<>();
        int id = 1;
        for (Map.Entry<String, List<Task>> entry : tasksByActivity.entrySet()) {
            Activity act = entry.getValue().get(0).getActivity();
            double totalQty = entry.getValue().stream().mapToDouble(Task::getQuantity).sum();
            Map<String, Double> qtyMap = entry.getValue().stream().collect(Collectors.toMap(t -> t.getLocation().toString(), Task::getQuantity));
            list.add(new ActivitySummaryDTO(id++, act.getName(), act.getTrade(), act.getCrewSize(), act.getDailyProductivity(), totalQty, qtyMap));
        }
        return list;
    }

    private List<ConstraintSummaryDTO> generateConstraintSummaries(List<PrecedenceConstraint> pc, List<ActivityConstraint> ac, List<LocationPrecedenceConstraint> lpc) {
        List<ConstraintSummaryDTO> list = new ArrayList<>();
        for (PrecedenceConstraint c : pc) list.add(new ConstraintSummaryDTO("Logic", c.getPredecessor() + " -> " + c.getSuccessor()));
        for (ActivityConstraint c : ac) list.add(new ConstraintSummaryDTO("Resource", c.getActivityName() + ": " + c.getType()));
        for (LocationPrecedenceConstraint c : lpc) list.add(new ConstraintSummaryDTO("Location", "Location Priority Defined"));
        return list;
    }

    private List<SolutionDTO> filterParetoFront(List<SolutionDTO> solutions) {
        List<SolutionDTO> pareto = new ArrayList<>();

        for (SolutionDTO candidate : solutions) {
            boolean isDominated = false;

            for (SolutionDTO other : solutions) {
                if (candidate == other) continue;

                boolean allBetterOrEqual =
                        other.getMakespan() <= candidate.getMakespan() &&
                                other.getPeakWorkforce() <= candidate.getPeakWorkforce() &&
                                other.getResourceFluctuation() <= candidate.getResourceFluctuation() &&
                                other.getWorkContinuity() <= candidate.getWorkContinuity() &&
                                other.getTotalCost() <= candidate.getTotalCost();

                boolean atLeastOneBetter =
                        other.getMakespan() < candidate.getMakespan() ||
                                other.getPeakWorkforce() < candidate.getPeakWorkforce() ||
                                other.getResourceFluctuation() < candidate.getResourceFluctuation() ||
                                other.getWorkContinuity() < candidate.getWorkContinuity() ||
                                other.getTotalCost() < candidate.getTotalCost();

                if (allBetterOrEqual && atLeastOneBetter) {
                    isDominated = true;
                    break;
                }
            }
            if (!isDominated) pareto.add(candidate);
        }
        return pareto.stream().distinct().collect(Collectors.toList());
    }

    /**
     * 专为论文测试设计的五维目标超体积 (HV) 归一化计算器
     */
    private double calculateHVForThesis(List<DoubleSolution> solutions) {

        if (solutions == null || solutions.isEmpty()) {
            return 0.0;
        }

        int numObjectives = solutions.get(0).objectives().length;

        // =============================================================
        // 1. 寻找 Ideal Point (最优) 和 Nadir Point (最差)
        // =============================================================
        double[] idealPoint = new double[numObjectives];
        double[] nadirPoint = new double[numObjectives];

        Arrays.fill(idealPoint, Double.MAX_VALUE);
        Arrays.fill(nadirPoint, -Double.MAX_VALUE);

        for (DoubleSolution sol : solutions) {
            for (int i = 0; i < numObjectives; i++) {
                double val = sol.objectives()[i];

                if (val < idealPoint[i]) {
                    idealPoint[i] = val;
                }

                if (val > nadirPoint[i]) {
                    nadirPoint[i] = val;
                }
            }
        }

        // 打印边界（论文可用）
        System.out.println("▶ 算法搜索边界 (Ideal Point 最优极值): "
                + Arrays.toString(idealPoint));

        System.out.println("▶ 算法搜索边界 (Nadir Point 最差极值): "
                + Arrays.toString(nadirPoint));

        // =============================================================
        // 2. 归一化到 [0,1]
        // =============================================================
        double[][] normalizedFront = new double[solutions.size()][numObjectives];

        for (int s = 0; s < solutions.size(); s++) {
            for (int i = 0; i < numObjectives; i++) {

                double maxMinDiff = nadirPoint[i] - idealPoint[i];

                if (maxMinDiff < 1e-6) {
                    normalizedFront[s][i] = 0.0; // 防止除0
                } else {
                    normalizedFront[s][i] =
                            (solutions.get(s).objectives()[i] - idealPoint[i]) / maxMinDiff;
                }
            }
        }

        // =============================================================
        // 3. 构造参考点 (Reference Point)
        // =============================================================
        double[] referencePoint = new double[numObjectives];
        Arrays.fill(referencePoint, 1.1);

        // =============================================================
        // 4. 计算 HV
        // =============================================================
        PISAHypervolume hvCalculator = new PISAHypervolume(referencePoint);

        // ⚠️ jMetal 版本差异：
        // - 有的版本是 compute()
        // - 有的版本是 evaluate()
        return hvCalculator.compute(normalizedFront);
    }

    private double calculateSpacingForThesis(List<DoubleSolution> solutions) {
        int n = solutions.size();
        if (n < 2) return 0.0; // 只有一个解，谈不上间距

        int numObjectives = solutions.get(0).objectives().length;

        // 1. 寻找极值，用于严谨的归一化处理
        double[] minObj = new double[numObjectives];
        double[] maxObj = new double[numObjectives];
        Arrays.fill(minObj, Double.MAX_VALUE);
        Arrays.fill(maxObj, -Double.MAX_VALUE);

        for (DoubleSolution sol : solutions) {
            for (int j = 0; j < numObjectives; j++) {
                minObj[j] = Math.min(minObj[j], sol.objectives()[j]);
                maxObj[j] = Math.max(maxObj[j], sol.objectives()[j]);
            }
        }

        // 2. 将所有解的目标值归一化到 [0.0, 1.0] 的超立方体中
        double[][] normalizedObj = new double[n][numObjectives];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < numObjectives; j++) {
                double range = maxObj[j] - minObj[j];
                if (range < 1e-6) {
                    normalizedObj[i][j] = 0.0;
                } else {
                    normalizedObj[i][j] = (solutions.get(i).objectives()[j] - minObj[j]) / range;
                }
            }
        }

        // 3. 计算每个解到其最近邻居的距离 (曼哈顿距离) d_i
        double[] d = new double[n];
        double sumD = 0.0;

        for (int i = 0; i < n; i++) {
            double minDist = Double.MAX_VALUE;
            for (int j = 0; j < n; j++) {
                if (i == j) continue; // 不和自己算距离

                double dist = 0.0;
                for (int k = 0; k < numObjectives; k++) {
                    dist += Math.abs(normalizedObj[i][k] - normalizedObj[j][k]);
                }
                if (dist < minDist) {
                    minDist = dist;
                }
            }
            d[i] = minDist;
            sumD += d[i];
        }

        // 4. 计算平均距离 mean(d)
        double meanD = sumD / n;

        // 5. 计算标准差，即 Spacing (SP) 指标
        double sumVariance = 0.0;
        for (int i = 0; i < n; i++) {
            sumVariance += Math.pow(d[i] - meanD, 2);
        }

        return Math.sqrt(sumVariance / (n - 1));
    }

    private static class AlgoStats {
        List<Long> times = new ArrayList<>();
        List<Double> hvList = new ArrayList<>();
        List<Double> spList = new ArrayList<>();
        List<Integer> pfSizes = new ArrayList<>();

        void add(long time, double hv, double sp, int pfSize) {
            times.add(time);
            hvList.add(hv);
            spList.add(sp);
            pfSizes.add(pfSize);
        }

        double avgTime() {
            return times.stream().mapToLong(Long::longValue).average().orElse(0);
        }

        double bestTime() {
            return times.stream().mapToLong(Long::longValue).min().orElse(0);
        }

        double avgHV() {
            return hvList.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        }

        double bestHV() {
            return hvList.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        }

        double avgSP() {
            return spList.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        }

        double bestSP() {
            return spList.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        }

        double avgPF() {
            return pfSizes.stream().mapToInt(Integer::intValue).average().orElse(0);
        }

        int bestPF() {
            return pfSizes.stream().mapToInt(Integer::intValue).max().orElse(0);
        }

        int minPF() {
            return pfSizes.stream().mapToInt(Integer::intValue).min().orElse(0);
        }
    }
}