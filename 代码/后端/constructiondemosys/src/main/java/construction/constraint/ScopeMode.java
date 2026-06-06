package construction.constraint;

public enum ScopeMode {

    GLOBAL,   // 空 scope：所有地点同一等价类
    PARTIAL,  // 使用给定维度集合
    EACH      // 所有维度（完整 location）
}
