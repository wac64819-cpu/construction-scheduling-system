package construction.dto;

import construction.constraint.PredType;
import construction.constraint.TemporalRelation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LocationPrecedenceConstraintDTO {

    // 对应前端 JSON 中的 loc1Patterns: ["*", "SZ1", "*"]
    private List<String> loc1Patterns = new ArrayList<>();

    // 对应前端 JSON 中的 loc2Patterns: ["*", "SZ2", "*"]
    private List<String> loc2Patterns = new ArrayList<>();

    private int dayGap;
    private PredType predType;

    // 对应前端 JSON 中的 applyActivities: ["ActivityA", "ActivityB"]
    private Set<String> applyActivities = new HashSet<>();

    private TemporalRelation relation;

    // 1. 无参构造函数
    public LocationPrecedenceConstraintDTO() {
    }

    // 2. 全参构造函数
    public LocationPrecedenceConstraintDTO(List<String> loc1Patterns, List<String> loc2Patterns,
                                           int dayGap, PredType predType,
                                           Set<String> applyActivities, TemporalRelation relation) {
        this.loc1Patterns = loc1Patterns;
        this.loc2Patterns = loc2Patterns;
        this.dayGap = dayGap;
        this.predType = predType;
        this.applyActivities = applyActivities;
        this.relation = relation;
    }

    // 3. Getters and Setters
    public List<String> getLoc1Patterns() { return loc1Patterns; }
    public void setLoc1Patterns(List<String> loc1Patterns) { this.loc1Patterns = loc1Patterns; }

    public List<String> getLoc2Patterns() { return loc2Patterns; }
    public void setLoc2Patterns(List<String> loc2Patterns) { this.loc2Patterns = loc2Patterns; }

    public int getDayGap() { return dayGap; }
    public void setDayGap(int dayGap) { this.dayGap = dayGap; }

    public PredType getPredType() { return predType; }
    public void setPredType(PredType predType) { this.predType = predType; }

    public Set<String> getApplyActivities() { return applyActivities; }
    public void setApplyActivities(Set<String> applyActivities) { this.applyActivities = applyActivities; }

    public TemporalRelation getRelation() { return relation; }
    public void setRelation(TemporalRelation relation) { this.relation = relation; }
}