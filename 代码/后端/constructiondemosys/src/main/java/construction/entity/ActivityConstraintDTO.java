package construction.dto;

import construction.constraint.ActivityConstraintType;
import construction.constraint.ScopeMode;

import java.util.HashSet;
import java.util.Set;

public class ActivityConstraintDTO {

    private String activityName;
    private ActivityConstraintType type;
    private ScopeMode scopeMode;
    // 对应前端传来的层级索引数组
    private Set<Integer> scopeLevels = new HashSet<>();

    // 1. 无参构造函数
    public ActivityConstraintDTO() {
    }

    // 2. 全参构造函数
    public ActivityConstraintDTO(String activityName, ActivityConstraintType type,
                                 ScopeMode scopeMode, Set<Integer> scopeLevels) {
        this.activityName = activityName;
        this.type = type;
        this.scopeMode = scopeMode;
        this.scopeLevels = scopeLevels;
    }

    // 3. Getters and Setters
    public String getActivityName() { return activityName; }
    public void setActivityName(String activityName) { this.activityName = activityName; }

    public ActivityConstraintType getType() { return type; }
    public void setType(ActivityConstraintType type) { this.type = type; }

    public ScopeMode getScopeMode() { return scopeMode; }
    public void setScopeMode(ScopeMode scopeMode) { this.scopeMode = scopeMode; }

    public Set<Integer> getScopeLevels() { return scopeLevels; }
    public void setScopeLevels(Set<Integer> scopeLevels) { this.scopeLevels = scopeLevels; }
}