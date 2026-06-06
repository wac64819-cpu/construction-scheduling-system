package construction.constraint.validator;

import construction.constraint.LocationPrecedenceConstraint;
import construction.constraint.PrecedenceConstraint;
import construction.constraint.ScopeMatcher;
import construction.constraint.temporal.DifferenceEdge;
import construction.constraint.temporal.TemporalMath;
import construction.model.Task;

import java.util.*;

public class TemporalConsistencyChecker {

    public static void validate(
            List<Task> tasks,
            List<PrecedenceConstraint> precedences,
            List<LocationPrecedenceConstraint> locationPrecedences) {

        int n = tasks.size();

        Map<Task, Integer> index = new HashMap<>();
        for (int i = 0; i < n; i++) {
            index.put(tasks.get(i), i);
        }

        List<DifferenceEdge> edges = new ArrayList<>();

        for (PrecedenceConstraint pc : precedences) {

            for (Task pred : tasks) {

                if (!pred.getActivity().getName().equals(pc.getPredecessor()))
                    continue;

                for (Task succ : tasks) {

                    if (!succ.getActivity().getName().equals(pc.getSuccessor()))
                        continue;

                    // ===== Scope 判断 (已更新为动态层级逻辑) =====
                    boolean sameScope;
                    switch (pc.getScopeMode()) {

                        case GLOBAL:
                            sameScope = true;
                            break;

                        case PARTIAL:
                            // ✅ 修改：传入 Set<Integer>
                            sameScope = ScopeMatcher.sameScope(
                                    pred.getLocation(),
                                    succ.getLocation(),
                                    pc.getScopeLevels()); // 请确保 PrecedenceConstraint 已更新此 Getter
                            break;

                        case EACH:
                            // ✅ 修改：动态生成所有层级的索引
                            sameScope = ScopeMatcher.sameScope(
                                    pred.getLocation(),
                                    succ.getLocation(),
                                    getAllLevelIndices(pred.getLocation().getDepth()));
                            break;

                        default:
                            sameScope = false;
                    }

                    if (!sameScope)
                        continue;
                    // =====================================================

                    int i = index.get(pred);
                    int j = index.get(succ);

                    // 注意：TemporalMath 计算逻辑通常只依赖 double/int，不需要改动
                    double delta = TemporalMath.computeDelta(
                            pred.getDuration(),
                            succ.getDuration(),
                            pc.getDayGap(),
                            pc.getPredType());

                    switch (pc.getRelation()) {
                        case GE:
                            edges.add(new DifferenceEdge(j, i, -delta));
                            break;
                        case LE:
                            edges.add(new DifferenceEdge(i, j, delta));
                            break;
                        case EQ:
                            edges.add(new DifferenceEdge(j, i, -delta));
                            edges.add(new DifferenceEdge(i, j, delta));
                            break;
                    }
                }
            }
        }

        for (LocationPrecedenceConstraint lpc : locationPrecedences) {

            for (Task t1 : tasks) {

                if (!lpc.getApplyActivities().contains("ALL")
                        && !lpc.getApplyActivities().contains(t1.getActivity().getName()))
                    continue;

                // LocationPattern.matches 已经改为支持动态 List，这里无需修改
                if (!lpc.getLoc1().matches(t1.getLocation()))
                    continue;

                for (Task t2 : tasks) {

                    if (!t2.getActivity().getName().equals(t1.getActivity().getName()))
                        continue;

                    if (!lpc.getLoc2().matches(t2.getLocation()))
                        continue;

                    int i = index.get(t1);
                    int j = index.get(t2);

                    double delta = TemporalMath.computeDelta(
                            t1.getDuration(),
                            t2.getDuration(),
                            lpc.getDayGap(),
                            lpc.getPredType());

                    switch (lpc.getRelation()) {
                        case GE:
                            edges.add(new DifferenceEdge(j, i, -delta));
                            break;
                        case LE:
                            edges.add(new DifferenceEdge(i, j, delta));
                            break;
                        case EQ:
                            edges.add(new DifferenceEdge(j, i, -delta));
                            edges.add(new DifferenceEdge(i, j, delta));
                            break;
                    }
                }
            }
        }

        // Bellman–Ford 算法检测负环（保持不变）
        double[] dist = new double[n];
        Arrays.fill(dist, 0.0);

        // 松弛 n-1 次
        for (int k = 0; k < n - 1; k++) {
            for (DifferenceEdge e : edges) {
                if (dist[e.from] + e.weight < dist[e.to]) {
                    dist[e.to] = dist[e.from] + e.weight;
                }
            }
        }

        // 第 n 次检测负环
        for (DifferenceEdge e : edges) {
            if (dist[e.from] + e.weight < dist[e.to]) {
                throw new IllegalStateException(
                        "Temporal window inconsistent (negative cycle detected)");
            }
        }
    }

    /**
     * 辅助方法：生成从 0 到 depth-1 的索引集合
     */
    private static Set<Integer> getAllLevelIndices(int depth) {
        Set<Integer> levels = new HashSet<>();
        for (int i = 0; i < depth; i++) {
            levels.add(i);
        }
        return levels;
    }
}