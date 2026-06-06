package construction.constraint;

import java.util.Set;

public class ActivityConstraint {

    private final String activityName;
    private final ActivityConstraintType type;

    private final ScopeMode scopeMode;
    private final Set<Integer> scopeLevels;

    public ActivityConstraint(String activityName,
                              ActivityConstraintType type,
                              ScopeMode scopeMode,
                              Set<Integer> scopeLevels) {
        this.activityName = activityName;
        this.type = type;
        this.scopeMode = scopeMode;
        this.scopeLevels = scopeLevels;
    }

    public String getActivityName() {
        return activityName;
    }

    public ActivityConstraintType getType() {
        return type;
    }

    public ScopeMode getScopeMode() {
        return scopeMode;
    }

    public Set<Integer> getScopeLevels() { return scopeLevels; }
}

