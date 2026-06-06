package construction.constraint;

import java.util.Set;

public class PrecedenceConstraint {

    private final String predecessor;
    private final String successor;

    private final ScopeMode scopeMode;
    // ✅ 修改：使用 Integer 代表层级索引 (0, 1, 2...)
    private final Set<Integer> scopeLevels;

    private final int dayGap;
    private final PredType predType;
    private final TemporalRelation relation;

    public PrecedenceConstraint(String predecessor,
                                String successor,
                                ScopeMode scopeMode,
                                Set<Integer> scopeLevels, // ✅ 修改
                                int dayGap,
                                PredType predType,
                                TemporalRelation relation) {
        this.predecessor = predecessor;
        this.successor = successor;
        this.scopeMode = scopeMode;
        this.scopeLevels = scopeLevels;
        this.dayGap = dayGap;
        this.predType = predType;
        this.relation = relation;
    }

    public String getPredecessor() { return predecessor; }
    public String getSuccessor() { return successor; }
    public ScopeMode getScopeMode() { return scopeMode; }
    public Set<Integer> getScopeLevels() { return scopeLevels; } // ✅ 修改 getter
    public int getDayGap() { return dayGap; }
    public PredType getPredType() { return predType; }
    public TemporalRelation getRelation() { return relation; }
}