package construction.constraint;

import java.util.Set;

public class LocationPrecedenceConstraint {

    private final LocationPattern loc1;
    private final LocationPattern loc2;

    private final int dayGap;
    private final PredType predType;

    private final Set<String> applyActivities;
    private final TemporalRelation relation;

    public LocationPrecedenceConstraint(
            LocationPattern loc1,
            LocationPattern loc2,
            int dayGap,
            PredType predType,
            Set<String> applyActivities,
            TemporalRelation relation
            ) {

        this.loc1 = loc1;
        this.loc2 = loc2;
        this.dayGap = dayGap;
        this.predType = predType;
        this.applyActivities = applyActivities;
        this.relation = relation;
    }

    public LocationPattern getLoc1() { return loc1; }
    public LocationPattern getLoc2() { return loc2; }
    public int getDayGap() { return dayGap; }
    public PredType getPredType() { return predType; }
    public Set<String> getApplyActivities() { return applyActivities; }
    public TemporalRelation getRelation() {return relation;}
}


