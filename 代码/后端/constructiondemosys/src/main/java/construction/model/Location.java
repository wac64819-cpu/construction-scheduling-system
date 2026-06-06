package construction.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

// 实现 Comparable 接口，支持通用排序
public class Location implements Comparable<Location> {

    private  List<String> hierarchy;
    // ✅ 新增无参构造函数 (供 MongoDB 反序列化使用)
    public Location() {
        this.hierarchy = new ArrayList<>();
    }
    public Location(String... path) {
        if (path == null) {
            this.hierarchy = new ArrayList<>();
        } else {
            this.hierarchy = new ArrayList<>(Arrays.asList(path));
        }
    }


    public List<String> getHierarchy() {
        return Collections.unmodifiableList(hierarchy);
    }

    // 通用获取方法：根据索引获取值
    public String getLevel(int index) {
        if (index >= 0 && index < hierarchy.size()) {
            return hierarchy.get(index);
        }
        return null;
    }

    // 获取层级深度
    public int getDepth() {
        return hierarchy.size();
    }

    @Override
    public String toString() {
        return "<" + String.join(", ", hierarchy) + ">";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Location location = (Location) o;
        return hierarchy.equals(location.hierarchy);
    }

    @Override
    public int hashCode() {
        return hierarchy.hashCode();
    }

    // ✅ 新增：通用字典序比较，用于排序
    @Override
    public int compareTo(Location other) {
        int len1 = this.hierarchy.size();
        int len2 = other.hierarchy.size();
        int lim = Math.min(len1, len2);

        for (int k = 0; k < lim; k++) {
            String s1 = this.hierarchy.get(k);
            String s2 = other.hierarchy.get(k);
            int cmp = s1.compareTo(s2);
            if (cmp != 0) {
                return cmp;
            }
        }
        return len1 - len2;
    }
}