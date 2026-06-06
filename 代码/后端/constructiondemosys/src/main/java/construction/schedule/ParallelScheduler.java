package construction.schedule;

import construction.constraint.*;
import construction.constraint.validator.PrecedenceGraphValidator;
import construction.constraint.validator.TemporalConsistencyChecker;
import construction.model.Task;
import org.uma.jmetal.solution.doublesolution.DoubleSolution;

import java.util.*;

public class ParallelScheduler implements Scheduler {

    @Override
    public ScheduleResult generateSchedule(
            DoubleSolution solution,
            List<Task> tasks,
            List<PrecedenceConstraint> precedences,
            List<ActivityConstraint> activityConstraints,
            List<LocationPrecedenceConstraint> locationPrecedences,
            Map<String, Integer> tradeCapacities,
            double maxOutsourcingRatio // ✅ 修改 1: 新增参数
    ) {

        //PrecedenceGraphValidator.validateDAG(tasks, precedences);
        //TemporalConsistencyChecker.validate(tasks, precedences, locationPrecedences);

        // System.out.println("========== START SCHEDULING ==========");

        Map<Task, Double> priorityMap = new HashMap<>();
        for (int i = 0; i < tasks.size(); i++) {
            priorityMap.put(tasks.get(i), solution.variables().get(i));
        }
        //任务对之间约束数量静态统计
        /*int p = countPrecedencePairs(tasks, precedences);
        int l = countLocationPairs(tasks, locationPrecedences);
        int a = countActivityPairs(tasks, activityConstraints);

        System.out.println("=== Static Constraint Pairs ===");
        System.out.println("Precedence: " + p);
        System.out.println("Location: " + l);
        System.out.println("Activity: " + a);
        System.out.println("Total: " + (p + l + a));*/


        List<Task> unscheduled = new ArrayList<>(tasks);
        unscheduled.sort(Comparator.comparing(priorityMap::get));

        Set<Task> activeTasks = new HashSet<>();
        Set<Task> finishedTasks = new HashSet<>();

        PriorityQueue<Task> eventQueue =
                new PriorityQueue<>(Comparator.comparing(Task::getEndTime));

        double time = 0.0;
        double peakWorkforce = 0.0;

        while (!unscheduled.isEmpty() || !activeTasks.isEmpty()) {

            boolean started = tryStartTasks(
                    time,
                    unscheduled,
                    activeTasks,
                    finishedTasks,
                    eventQueue,
                    tasks,
                    precedences,
                    activityConstraints,
                    locationPrecedences,
                    tradeCapacities,
                    maxOutsourcingRatio // ✅ 修改 2: 传递参数
            );

            if (!started) {
                double nextCompletion = nextCompletionTime(eventQueue);

                double nextWindowTime = nextWindowReleaseTime(
                        unscheduled,
                        finishedTasks,
                        activeTasks,
                        precedences,
                        locationPrecedences,
                        tasks,
                        time
                );

                double nextTime = Math.ceil(Math.min(nextCompletion, nextWindowTime));//nextTime是nextCompletion（最早完成时间）与nextWindowTime（约束释放时间）
                double current = computeCurrentWorkforce(activeTasks);//
                peakWorkforce = Math.max(peakWorkforce, current);

                if (nextTime == Double.MAX_VALUE) {
                    System.err.println("DEADLOCK at time = " + time);
                    throw new IllegalStateException("Deadlock: no future event");
                }//死锁

                time = nextTime;//调度器的时钟推进到下一个重要事件

                if (nextCompletion <= nextWindowTime) {
                    processCompletion(time, eventQueue, activeTasks, finishedTasks);//如果下一个事件是某任务完成（即 nextCompletion ≤ nextWindowTime），
                }
            }
        }

        // System.out.println("========== END SCHEDULING ==========");
        // System.out.printf("MAKESPAN = %.1f | PEAK WORKFORCE = %.0f%n", time, peakWorkforce);

        return new ScheduleResult(time, peakWorkforce);
    }

    // ... [precedenceOK 保持不变] ...
    private boolean precedenceOK(
            Task task,
            double currentTime,
            Set<Task> finishedTasks,
            Set<Task> activeTasks,
            List<Task> allTasks,
            List<PrecedenceConstraint> precedences) {

        for (PrecedenceConstraint pc : precedences) {//遍历约束
            if (!task.getActivity().getName().equals(pc.getSuccessor())) {
                continue;
            }//如果当前task的activity名字不等于约束关系的先后关系的后活动，则跳过

            for (Task predecessor : allTasks) {
                if (!predecessor.getActivity().getName().equals(pc.getPredecessor())) {
                    continue;
                }//遍历所有的task，如果这个task的activity名字不等于约束前后关系的前活动名字，则跳过

                boolean sameClass = false;//初始化等价类为false

                switch (pc.getScopeMode()) {
                    case GLOBAL:
                        sameClass = true;//如果是global，则默认处于同一等价类
                        break;

                    case PARTIAL://如果是PARTIAL，则进行逐级匹配
                        sameClass = ScopeMatcher.sameScope(
                                predecessor.getLocation(),
                                task.getLocation(),
                                pc.getScopeLevels());
                        break;

                    case EACH://如果是each，则必须所有层级都匹配
                        sameClass = ScopeMatcher.sameScope(
                                predecessor.getLocation(),
                                task.getLocation(),
                                getAllLevelIndices(task.getLocation().getDepth()));
                        break;
                }

                if (!sameClass) continue;//如果不是同一等价类，则跳过

                // 🚨 核心修复：检查前置任务是否已经启动！
                boolean predHasStarted = activeTasks.contains(predecessor) || finishedTasks.contains(predecessor);
                if (!predHasStarted) {
                    // 前置任务连时间轴都没进（它的 startTime 是假数据 0.0）
                    // 后置任务绝对不允许启动，立刻拦截！
                    return false;
                }
                if (pc.getPredType() == PredType.FS && !finishedTasks.contains(predecessor)) {
                    return false;
                }//如果是FS关系，并且未完成的task中包含前后关系约束的前者，则判断为违反该约束

                if (!checkTemporalRelation(predecessor, currentTime, task.getDuration(), pc.getPredType(), pc.getDayGap())) {
                    return false;
                }//这里 predStart/predFinish 是前置 Task 的 start/end，循环会遍历 所有符合条件的前置 Task，返回 false 如果任何一个 Task 不满足 → 最终效果等于用该前置 Activity 的最严格 Task 时间
            }
        }
        return true;
    }

    // ... [activityConstraintOK 保持不变] ...
    private boolean activityConstraintOK(
            Task candidateTask,
            Set<Task> activeTasks,
            List<ActivityConstraint> activityConstraints) {

        for (ActivityConstraint ac : activityConstraints) {
            if (ac.getType() != ActivityConstraintType.EXCLUSIVE) {
                continue;
            }

            // 判断候选任务（当前正在调度的这个任务）和正在执行的任务，是否属于被约束的“排他性活动”
            boolean candidateIsExclusive = candidateTask.getActivity().getName().equals(ac.getActivityName());

            for (Task activeTask : activeTasks) {
                boolean activeIsExclusive = activeTask.getActivity().getName().equals(ac.getActivityName());

                // 规则 1：如果两task都不是排他性活动，本条规则不管，直接放行
                if (!candidateIsExclusive && !activeIsExclusive) {
                    continue;
                }

                // 规则 2：如果两task都是同一种排他性活动，允许协同作业！直接放行
                if (candidateIsExclusive && activeIsExclusive) {
                    continue;
                }

                // 运行到这里说明：两task中必定【有且仅有一个】是排他性，另一个是普通任务（异类）
                // 此时，必须检查它们是否处于同一个等价类空间！
                boolean sameEquivalenceClass = false;

                switch (ac.getScopeMode()) {
                    case GLOBAL:
                        sameEquivalenceClass = true;
                        break;
                    case PARTIAL:
                        sameEquivalenceClass = ScopeMatcher.sameScope(
                                candidateTask.getLocation(),
                                activeTask.getLocation(),
                                ac.getScopeLevels());
                        break;
                    case EACH:
                        sameEquivalenceClass = ScopeMatcher.sameScope(
                                candidateTask.getLocation(),
                                activeTask.getLocation(),
                                getAllLevelIndices(candidateTask.getLocation().getDepth()));
                        break;
                }

                // 如果属于同一等价类，且是异类任务，立即拦截
                if (sameEquivalenceClass) {

                    return false;
                }
            }
        }
        return true; // 没有任何空间与工种冲突，允许进场
    }

    // ... [locationPrecedenceOK 保持不变] ...
    private boolean locationPrecedenceOK(
            Task task,
            Set<Task> finishedTasks,
            Set<Task> activeTasks,
            List<Task> allTasks,
            List<LocationPrecedenceConstraint> locationPrecedences,
            double currentTime) {

        for (LocationPrecedenceConstraint lpc : locationPrecedences) {

            if (!lpc.getApplyActivities().contains("ALL")
                    && !lpc.getApplyActivities().contains(task.getActivity().getName())) {
                continue;//根据activity名，找出这条约束规定哪些task
            }

            if (!lpc.getLoc2().matches(task.getLocation())) {
                continue;//当前需要调度的task的location需要与约束的后序要求的location相匹配
            }

            for (Task t1 : allTasks) {//此处寻找与约束的前序activity+location相匹配的task
                if (!t1.getActivity().getName().equals(task.getActivity().getName())) {
                    continue;//activity相同
                }
                if (!lpc.getLoc1().matches(t1.getLocation())) {
                    continue;//遍历到的task的location与约束规定的前序location相匹配
                }

                // 🚨 核心修复：检查空间前置任务是否已经启动
                boolean predHasStarted = activeTasks.contains(t1) || finishedTasks.contains(t1);
                if (!predHasStarted) return false;

                if (lpc.getPredType() == PredType.FS && !finishedTasks.contains(t1)) {
                    return false;
                }

                if (!checkTemporalRelation(t1, currentTime, task.getDuration(), lpc.getPredType(), lpc.getDayGap())) {
                    return false;
                }
            }
        }
        return true;
    }

    // --------------------------------------------------------
    // ✅ 修改 3: 核心逻辑修改 - 资源容量检查
    // --------------------------------------------------------
    private boolean tradeCapacityOK(
            Task task,
            Set<Task> activeTasks,
            Map<String, Integer> tradeCapacities,
            double maxOutsourcingRatio) { // 接收外包比例

        String trade = task.getActivity().getTrade();
        int required = task.getActivity().getCrewSize();

        int used = 0;
        for (Task t : activeTasks) {
            if (t.getActivity().getTrade().equals(trade)) {
                used += t.getActivity().getCrewSize();
            }
        }

        // 获取该工种的基础容量（自有资源）
        int baseCapacity = tradeCapacities.getOrDefault(trade, 0);

        // ✅ 计算允许的最大上限 = 自有 + (自有 * 外包比例)
        int maxLimit = (int) (baseCapacity * (1.0 + maxOutsourcingRatio));

        // 检查是否在放宽后的限制内
        return used + required <= maxLimit;
    }

    private boolean checkTemporalRelation(Task pred, double candidateStart, double candidateDuration, PredType type, int gap) {
        double predStart = pred.getStartTime();
        double predFinish = pred.getEndTime();
        double succStart = candidateStart;
        double succFinish = candidateStart + candidateDuration;

        switch (type) {
            case FS: return succStart >= predFinish + gap;
            case FF: return succFinish >= predFinish + gap;
            case SS: return succStart >= predStart + gap;
            case SF: return succFinish >= predStart + gap;
        }
        return true;
    }

    // --------------------------------------------------------
    // ✅ 修改 4: tryStartTasks 接收参数并传递
    // --------------------------------------------------------
    private boolean tryStartTasks(//在当前时间 time，检查所有尚未开始的任务（unscheduled），尝试启动符合条件的任务，并更新活跃
                                  //任务集、事件队列和任务状态。返回值 true/false 表示是否至少启动了一个任务。
            double time,
            List<Task> unscheduled,
            Set<Task> activeTasks,
            Set<Task> finishedTasks,
            PriorityQueue<Task> eventQueue,//按结束时间排序的优先队列
            List<Task> allTasks,
            List<PrecedenceConstraint> precedences,
            List<ActivityConstraint> activityConstraints,
            List<LocationPrecedenceConstraint> locationPrecedences,
            Map<String, Integer> tradeCapacities,
            double maxOutsourcingRatio) { // 接收参数

        boolean startedAny = false;//startedAny：标记当前时间是否启动了任务。
        Iterator<Task> it = unscheduled.iterator();//使用迭代器遍历尚未调度的任务列表，方便在循环中安全删除已启动任务

        while (it.hasNext()) {
            Task task = it.next();//对每个任务 task，判断是否可以在 当前时间启动

            if (precedenceOK(task, time,finishedTasks,activeTasks ,allTasks, precedences)
                    && activityConstraintOK(task, activeTasks, activityConstraints)
                    && locationPrecedenceOK(task, finishedTasks, activeTasks,allTasks, locationPrecedences, time)
                    // ✅ 传递参数给 capacity 检查
                    && tradeCapacityOK(task, activeTasks, tradeCapacities, maxOutsourcingRatio)) {

                double ceilStart = Math.ceil(time);
                task.setStartTime(ceilStart);//设置任务开始时间
                activeTasks.add(task);//将任务加入正在执行的集合
                eventQueue.add(task);//将任务加入按结束时间排序的优先队列，用于后续完成事件
                it.remove();//从未开始列表中删除
                startedAny = true;//标记至少启动了一个任务

                // 打印日志 (可选)
                // System.out.printf("[%.1f] START %s | Outsourcing Ratio: %.2f%n", time, task.getActivity().getName(), maxOutsourcingRatio);
            }
        }
        return startedAny;//如果是false → 当前时间没有任务可启动（可能需要跳到下一个事件时间）
    }

    // ... [后续方法保持不变] ...
    private double nextCompletionTime(PriorityQueue<Task> eventQueue) {
        if (eventQueue.isEmpty()) return Double.MAX_VALUE;
        return Math.ceil(eventQueue.peek().getEndTime());
    }//返回当前活跃任务中最早完成的任务的结束时间（即下一个“事件”发生的时间），如果没有活跃任务，则返回 Double.MAX_VALUE

    private double nextWindowReleaseTime(
            List<Task> unscheduled,
            Set<Task> finishedTasks,
            Set<Task> activeTasks,
            List<PrecedenceConstraint> precedences,
            List<LocationPrecedenceConstraint> locationPrecedences,
            List<Task> allTasks,
            double currentTime) {

        double min = Double.MAX_VALUE;
        for (Task task : unscheduled) {
            double t = computeEarliestFeasibleStart(task, allTasks,finishedTasks, activeTasks,precedences, locationPrecedences);
            if (t > currentTime) min = Math.min(min, t);
        }
        return Math.ceil(min);
    }//下一个尚未开始的任务最早可行启动时间（受前置约束、位置约束、活动约束等影响），即约束释放时间

    private double computeEarliestFeasibleStart(
            Task task,
            List<Task> allTasks,
            Set<Task> finishedTasks,
            Set<Task> activeTasks,
            List<PrecedenceConstraint> precedences,
            List<LocationPrecedenceConstraint> locationPrecedences) {

        double lower = 0.0;

        for (PrecedenceConstraint pc : precedences) {
            if (!pc.getSuccessor().equals(task.getActivity().getName())) continue;

            for (Task pred : allTasks) {
                if (!pred.getActivity().getName().equals(pc.getPredecessor())) continue;

                boolean sameScope;
                switch (pc.getScopeMode()) {
                    case GLOBAL:
                        sameScope = true;
                        break;
                    case PARTIAL:
                        sameScope = ScopeMatcher.sameScope(
                                pred.getLocation(),
                                task.getLocation(),
                                pc.getScopeLevels());
                        break;
                    case EACH:
                        sameScope = ScopeMatcher.sameScope(
                                pred.getLocation(),
                                task.getLocation(),
                                getAllLevelIndices(task.getLocation().getDepth()));
                        break;
                    default:
                        sameScope = false;
                }
                if (!sameScope) continue;

                // 🚨 核心修复：如果前置任务还没开工，这个任务的解禁时间就是未知的（无穷远）
                boolean predHasStarted = activeTasks.contains(pred) || finishedTasks.contains(pred);
                if (!predHasStarted) {
                    return Double.MAX_VALUE;
                }

                double bound = computeBound(pred, task, pc);
                lower = Math.max(lower, bound);
            }
        }

        for (LocationPrecedenceConstraint lpc : locationPrecedences) {
            if (!lpc.getApplyActivities().contains("ALL")
                    && !lpc.getApplyActivities().contains(task.getActivity().getName()))
                continue;

            if (!lpc.getLoc2().matches(task.getLocation())) continue;

            for (Task pred : allTasks) {
                if (!pred.getActivity().getName().equals(task.getActivity().getName())) continue;
                if (!lpc.getLoc1().matches(pred.getLocation())) continue;

                // 🚨 核心修复
                boolean predHasStarted = activeTasks.contains(pred) || finishedTasks.contains(pred);
                if (!predHasStarted) return Double.MAX_VALUE;

                double bound = computeLocationBound(pred, task, lpc);
                lower = Math.max(lower, bound);
            }
        }
        return Math.ceil(lower);
    }

    private double computeBound(Task pred, Task succ, PrecedenceConstraint pc) {//activitypredence时间下界
        switch (pc.getPredType()) {
            case FS: return pred.getEndTime() + pc.getDayGap();
            case SS: return pred.getStartTime() + pc.getDayGap();
            case FF: return pred.getEndTime() + pc.getDayGap() - succ.getDuration();
            case SF: return pred.getStartTime() + pc.getDayGap() - succ.getDuration();
            default: throw new IllegalArgumentException();
        }
    }

    private double computeLocationBound(Task pred, Task succ, LocationPrecedenceConstraint lpc) {//locationpredence时间下界
        switch (lpc.getPredType()) {
            case FS: return pred.getEndTime() + lpc.getDayGap();
            case SS: return pred.getStartTime() + lpc.getDayGap();
            case FF: return pred.getEndTime() + lpc.getDayGap() - succ.getDuration();
            case SF: return pred.getStartTime() + lpc.getDayGap() - succ.getDuration();
            default: throw new IllegalArgumentException();
        }
    }

    private void processCompletion(double time, PriorityQueue<Task> eventQueue, Set<Task> activeTasks, Set<Task> finishedTasks) {//处理已经完工的task
        Task finished = eventQueue.poll();
        activeTasks.remove(finished);
        finishedTasks.add(finished);
    }

    private double computeCurrentWorkforce(Set<Task> activeTasks) {//这个逻辑不用了
        return activeTasks.stream().mapToInt(t -> t.getActivity().getCrewSize()).sum();
    }

    private Set<Integer> getAllLevelIndices(int depth) {//生成全层级匹配集合
        Set<Integer> levels = new HashSet<>();
        for (int i = 0; i < depth; i++) {
            levels.add(i);
        }
        return levels;
    }

    public int countPrecedencePairs(
            List<Task> tasks,
            List<PrecedenceConstraint> precedences) {

        int count = 0;

        for (PrecedenceConstraint pc : precedences) {
            for (Task pred : tasks) {
                if (!pred.getActivity().getName().equals(pc.getPredecessor()))
                    continue;

                for (Task succ : tasks) {
                    if (!succ.getActivity().getName().equals(pc.getSuccessor()))
                        continue;
                    if (pred == succ) continue;
                    boolean sameScope = false;

                    switch (pc.getScopeMode()) {
                        case GLOBAL:
                            sameScope = true;
                            break;

                        case PARTIAL:
                            sameScope = ScopeMatcher.sameScope(
                                    pred.getLocation(),
                                    succ.getLocation(),
                                    pc.getScopeLevels());
                            break;

                        case EACH:
                            sameScope = ScopeMatcher.sameScope(
                                    pred.getLocation(),
                                    succ.getLocation(),
                                    getAllLevelIndices(pred.getLocation().getDepth()));
                            break;
                    }

                    if (sameScope) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    public int countLocationPairs(
            List<Task> tasks,
            List<LocationPrecedenceConstraint> lpcs) {

        int count = 0;

        for (LocationPrecedenceConstraint lpc : lpcs) {

            for (Task t1 : tasks) {

                if (!lpc.getLoc1().matches(t1.getLocation()))
                    continue;

                for (Task t2 : tasks) {
                    if (t1 == t2) continue;
                    if (!t2.getActivity().getName().equals(t1.getActivity().getName()))
                        continue;

                    if (!lpc.getLoc2().matches(t2.getLocation()))
                        continue;

                    if (!lpc.getApplyActivities().contains("ALL")
                            && !lpc.getApplyActivities().contains(t2.getActivity().getName()))
                        continue;

                    count++;
                }
            }
        }

        return count;
    }

    public int countActivityPairs(
            List<Task> tasks,
            List<ActivityConstraint> constraints) {

        int count = 0;

        for (ActivityConstraint ac : constraints) {

            if (ac.getType() != ActivityConstraintType.EXCLUSIVE)
                continue;

            for (int i = 0; i < tasks.size(); i++) {
                Task t1 = tasks.get(i);

                for (int j = i + 1; j < tasks.size(); j++) {
                    Task t2 = tasks.get(j);
                    if (t1 == t2) continue;
                    boolean t1Exclusive = t1.getActivity().getName().equals(ac.getActivityName());
                    boolean t2Exclusive = t2.getActivity().getName().equals(ac.getActivityName());

                    // 必须是“异类”
                    if (t1Exclusive == t2Exclusive)
                        continue;

                    boolean sameScope = false;

                    switch (ac.getScopeMode()) {
                        case GLOBAL:
                            sameScope = true;
                            break;

                        case PARTIAL:
                            sameScope = ScopeMatcher.sameScope(
                                    t1.getLocation(), t2.getLocation(), ac.getScopeLevels());
                            break;

                        case EACH:
                            sameScope = ScopeMatcher.sameScope(
                                    t1.getLocation(), t2.getLocation(),
                                    getAllLevelIndices(t1.getLocation().getDepth()));
                            break;
                    }

                    if (sameScope) {
                        count++;
                    }
                }
            }
        }

        return count;
    }
}