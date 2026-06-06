package construction.dto;

import construction.constraint.PredType;
import construction.constraint.ScopeMode;
import construction.constraint.TemporalRelation;

import java.util.HashSet;
import java.util.Set;

public class PrecedenceConstraintDTO {

    private String predecessor;
    private String successor;
    private ScopeMode scopeMode;
    // 对应前端传来的层级索引数组 [0, 2]
    private Set<Integer> scopeLevels = new HashSet<>();
    private int dayGap;
    private PredType predType;
    private TemporalRelation relation;

    // 1. 无参构造函数 (必须有，用于 Jackson/MongoDB 反序列化)
    public PrecedenceConstraintDTO() {
    }

    // 2. 全参构造函数
    public PrecedenceConstraintDTO(String predecessor, String successor, ScopeMode scopeMode,
                                   Set<Integer> scopeLevels, int dayGap,
                                   PredType predType, TemporalRelation relation) {
        this.predecessor = predecessor;
        this.successor = successor;
        this.scopeMode = scopeMode;
        this.scopeLevels = scopeLevels;
        this.dayGap = dayGap;
        this.predType = predType;
        this.relation = relation;
    }

    // 3. Getters and Setters
    public String getPredecessor() { return predecessor; }
    public void setPredecessor(String predecessor) { this.predecessor = predecessor; }

    public String getSuccessor() { return successor; }
    public void setSuccessor(String successor) { this.successor = successor; }

    public ScopeMode getScopeMode() { return scopeMode; }
    public void setScopeMode(ScopeMode scopeMode) { this.scopeMode = scopeMode; }

    public Set<Integer> getScopeLevels() { return scopeLevels; }
    public void setScopeLevels(Set<Integer> scopeLevels) { this.scopeLevels = scopeLevels; }

    public int getDayGap() { return dayGap; }
    public void setDayGap(int dayGap) { this.dayGap = dayGap; }

    public PredType getPredType() { return predType; }
    public void setPredType(PredType predType) { this.predType = predType; }

    public TemporalRelation getRelation() { return relation; }
    public void setRelation(TemporalRelation relation) { this.relation = relation; }
}