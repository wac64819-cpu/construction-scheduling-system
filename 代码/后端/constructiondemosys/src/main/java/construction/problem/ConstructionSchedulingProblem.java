package construction.problem;

import construction.constraint.ActivityConstraint;
import construction.constraint.LocationPrecedenceConstraint;
import construction.constraint.PrecedenceConstraint;
import construction.entity.ProjectSettings;
import construction.constraint.TradeData;

import construction.model.Task;
import construction.schedule.ScheduleResult;
import construction.schedule.Scheduler;
import org.uma.jmetal.problem.doubleproblem.impl.AbstractDoubleProblem;
import org.uma.jmetal.solution.doublesolution.DoubleSolution;

import java.util.*;
import java.util.stream.Collectors;

public class ConstructionSchedulingProblem extends AbstractDoubleProblem {

    private final List<Task> tasks;
    private final List<PrecedenceConstraint> precedences;
    private final Scheduler scheduler;
    private final List<ActivityConstraint> activityConstraints;
    private final List<LocationPrecedenceConstraint> locationPrecedences;
    private final Map<String, Integer> tradeCapacities;

    // ✅ 新增字段，用于成本计算
    private final double maxOutsourcingRatio;
    private final List<TradeData> tradeDataList;
    private final ProjectSettings settings;

    public ConstructionSchedulingProblem(
            List<Task> tasks,
            List<PrecedenceConstraint> precedences,
            List<ActivityConstraint> activityConstraints,
            List<LocationPrecedenceConstraint> locationPrecedences,
            Map<String, Integer> tradeCapacities,
            double maxOutsourcingRatio,
            List<TradeData> tradeDataList, // ✅ 修正：参数名改为 tradeDataList，与字段一致，且类型为 Domain Model
            ProjectSettings settings,
            Scheduler scheduler) {

        this.tasks = tasks;
        this.precedences = precedences;
        this.scheduler = scheduler;
        this.activityConstraints = activityConstraints;
        this.locationPrecedences = locationPrecedences;
        this.tradeCapacities = tradeCapacities;
        this.maxOutsourcingRatio = maxOutsourcingRatio;

        // ✅ 修正：赋值逻辑现在正确了
        this.tradeDataList = tradeDataList;
        this.settings = settings;

        // ✅ 5 个目标
        // 0: Makespan (工期)
        // 1: Peak Workforce (峰值人力)
        // 2: Resource Fluctuation (波动)
        // 3: Work Continuity (闲置)
        // 4: Total Cost (成本)
        this.numberOfObjectives = 5;

        List<Double> lowerLimit = new ArrayList<>(tasks.size());
        List<Double> upperLimit = new ArrayList<>(tasks.size());

        for (int i = 0; i < tasks.size(); i++) {
            lowerLimit.add(0.0);
            upperLimit.add(1.0);
        }

        variableBounds(lowerLimit, upperLimit);
    }

    @Override
    public DoubleSolution evaluate(DoubleSolution solution) {

        // 1. 生成调度方案
        ScheduleResult result = scheduler.generateSchedule(
                solution,
                tasks,
                precedences,
                activityConstraints,
                locationPrecedences,
                tradeCapacities, // 这里只传 Capacity Map 给 Scheduler 做硬约束检查
                maxOutsourcingRatio // 允许超额排程
        );

        // 2. 计算目标
        solution.objectives()[0] = result.getMakespan();
        // ✅ 核心修复：调用新增的按日统计峰值方法，替代 result.getPeakWorkforce()
        solution.objectives()[1] = calculatePeak(result);
        solution.objectives()[2] = calculateFluctuation(result);
        solution.objectives()[3] = calculateDiscontinuity();
        solution.objectives()[4] = calculateTotalCost(result);

        return solution;
    }

    // =================================================================
    // 辅助计算逻辑
    // =================================================================
    /**
     * 目标 2：峰值人力 (消除事件驱动的交接班瞬时误差)
     */
    private double calculatePeak(ScheduleResult result) {
        int maxDay = (int) Math.ceil(result.getMakespan());
        int[] dailyTotalUsage = new int[maxDay + 2];

        for (Task t : tasks) {
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
    /**
     * 目标 3：资源波动 (方差/相邻日变化平方和)
     */
    private double calculateFluctuation(ScheduleResult result) {
        int maxDay = (int) Math.ceil(result.getMakespan());
        // +2 防止数组越界
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

    /**
     * 目标 4：工作连续性 (闲置天数总和) - 【已修复并发重叠漏洞】
     */
    private double calculateDiscontinuity() {
        double totalIdleDays = 0.0;

        // 按工种分组
        Map<String, List<Task>> tasksByTrade = tasks.stream()
                .collect(Collectors.groupingBy(t -> t.getActivity().getTrade()));

        for (List<Task> tradeTasks : tasksByTrade.values()) {
            // 按开始时间排序
            tradeTasks.sort(Comparator.comparingDouble(Task::getStartTime));

            // 记录该工种“截至目前的最晚收工时间”
            double maxEndTimeSoFar = -1.0;

            for (Task t : tradeTasks) {
                if (maxEndTimeSoFar >= 0) { // 跳过第一个任务
                    // 间隙 = 当前任务的开始时间 - 之前所有任务的最晚结束时间
                    double gap = t.getStartTime() - maxEndTimeSoFar;
                    // 只有当产生了真正的断档时，才计算闲置
                    if (gap > 0.001) {
                        totalIdleDays += gap;
                    }
                }
                // 更新最晚收工时间 (取历史最晚和当前任务结束时间的最大值)
                maxEndTimeSoFar = Math.max(maxEndTimeSoFar, t.getEndTime());
            }
        }
        return totalIdleDays;
    }

    /**
     * 目标 5：总成本 (Total Cost)
     * = 间接成本 + 闲置惩罚 + 人力资源成本(含外包)
     */
    private double calculateTotalCost(ScheduleResult result) {
        double totalCost = 0.0;

        // 1. 间接成本 + 闲置惩罚
        if (settings != null) {
            totalCost += result.getMakespan() * settings.indirectCostPerDay;
            totalCost += calculateDiscontinuity() * settings.idleCostPerDay;
        }

        // 2. 计算每日资源消耗
        int maxDay = (int) Math.ceil(result.getMakespan());
        Map<String, int[]> tradeUsageMap = new HashMap<>();

        // 初始化数组
        for (TradeData trade : tradeDataList) {
            tradeUsageMap.put(trade.getTradeName(), new int[maxDay + 1]);
        }

        // 填充用量
        for (Task t : tasks) {
            String tradeName = t.getActivity().getTrade();
            // 防止 Excel 里的 Trade 名称和 TradeData 不匹配
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

        // 3. 计算资源费用
        for (TradeData trade : tradeDataList) {
            int[] dailyUsage = tradeUsageMap.get(trade.getTradeName());
            if (dailyUsage == null) continue;

            for (int used : dailyUsage) {
                if (used <= 0) continue;

                // 核心逻辑：自有 vs 外包
                if (used <= trade.getAvailableWorkers()) {
                    // 情况 A：全是自有
                    // 成本 = 用量 * 单价
                    totalCost += used * trade.getStandardRate();
                } else {
                    // 情况 B：自有 + 外包
                    int own = trade.getAvailableWorkers();
                    int outsourced = used - trade.getAvailableWorkers();

                    // 获取倍率 (Settings 里的倍率)
                    double multiplier = (settings != null) ? settings.outsourcingCostMultiplier : 1.5;

                    // 成本 = (自有量 * 单价) + (外包量 * 单价 * 倍率)
                    double dailyCost = (own * trade.getStandardRate()) +
                            (outsourced * trade.getStandardRate() * multiplier);

                    totalCost += dailyCost;
                }
            }
        }

        return totalCost;
    }
}
