package construction.constraint.validator;

import construction.constraint.PrecedenceConstraint;
import construction.constraint.PredType;
import construction.constraint.ScopeMatcher;
import construction.model.Task;

import java.util.*;

public class PrecedenceGraphValidator {

    public static void validateDAG(
            List<Task> tasks,
            List<PrecedenceConstraint> precedences) {

        Map<Task, List<Task>> graph = new HashMap<>();
        Map<Task, Integer> indegree = new HashMap<>();

        for (Task t : tasks) {
            graph.put(t, new ArrayList<>());
            indegree.put(t, 0);
        }

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
                            // 请确保 PrecedenceConstraint 类中 getter 已更名为 getScopeLevels()
                            sameScope = ScopeMatcher.sameScope(
                                    pred.getLocation(),
                                    succ.getLocation(),
                                    pc.getScopeLevels());
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
                    if (pc.getPredType() == PredType.FS && pc.getDayGap() >= 0){
                        graph.get(pred).add(succ);
                        indegree.put(succ, indegree.get(succ) + 1);
                    }


                }
            }
        }

        // Kahn 算法进行拓扑排序检测环
        Queue<Task> q = new LinkedList<>();
        for (Task t : indegree.keySet()) {
            if (indegree.get(t) == 0)
                q.add(t);
        }

        int visited = 0;

        while (!q.isEmpty()) {
            Task t = q.poll();
            visited++;

            for (Task next : graph.get(t)) {
                indegree.put(next, indegree.get(next) - 1);
                if (indegree.get(next) == 0)
                    q.add(next);
            }
        }

        if (visited != tasks.size()) {
            throw new IllegalStateException(
                    "Precedence graph has cycle");
        }
    }

    /**
     * 辅助方法：生成从 0 到 depth-1 的索引集合
     * 用于替代旧的 EnumSet.allOf(LocationDimension.class)
     */
    private static Set<Integer> getAllLevelIndices(int depth) {
        Set<Integer> levels = new HashSet<>();
        for (int i = 0; i < depth; i++) {
            levels.add(i);
        }
        return levels;
    }
}