package construction.constraint;

import construction.model.Location;
import java.util.Arrays;
import java.util.List;

public class LocationPattern {

    private final List<String> patterns;

    // ✅ 支持动态参数
    public LocationPattern(String... patterns) {
        this.patterns = Arrays.asList(patterns);
    }

    // ✅ 也支持 List 构造
    public LocationPattern(List<String> patterns) {
        this.patterns = patterns;
    }

    public boolean matches(Location l) {
        // 模式可能有 3 层，Location 可能有 4 层，通常匹配前 N 层
        for (int i = 0; i < patterns.size(); i++) {
            String pattern = patterns.get(i);
            String val = l.getLevel(i);

            // 模式要求有这一层，但地点没有 -> 不匹配
            if (val == null) {
                // 除非模式也是通配符，否则不匹配
                if (!"*".equals(pattern)) return false;
            } else {
                if (!match(pattern, val)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean match(String pattern, String value) {
        if (pattern == null || pattern.equals("*")) return true;
        return pattern.equals(value);
    }
}