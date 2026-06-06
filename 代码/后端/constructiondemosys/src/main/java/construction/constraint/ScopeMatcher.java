package construction.constraint;

import construction.model.Location;
import java.util.Set;

public class ScopeMatcher {

    public static boolean sameScope(Location l1,
                                    Location l2,
                                    Set<Integer> scopeLevels) { // ✅ 改为 Integer

        if (scopeLevels == null || scopeLevels.isEmpty()) {
            return true; // 如果没有指定层级，默认通过或视具体业务而定
        }

        for (Integer levelIndex : scopeLevels) {
            // 动态获取对应层级的值
            String val1 = l1.getLevel(levelIndex);
            String val2 = l2.getLevel(levelIndex);

            // 1. 如果某一方根本没有这一层（比如 location 只有 2 层，但约束要求检查第 3 层）
            // 视为不匹配 (或者你可以根据需求改为 continue)
            if (val1 == null || val2 == null) {
                return false;
            }

            // 2. 比较值
            if (!val1.equals(val2)) {
                return false;
            }
        }
        return true;
    }
}