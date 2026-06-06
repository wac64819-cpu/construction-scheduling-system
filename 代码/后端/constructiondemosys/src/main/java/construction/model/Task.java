package construction.model;

public class Task {

    private final Activity activity;
    private final Location location;
    private final double quantity;

    private double startTime ;
    private double endTime   ;

    public Task(Activity activity, Location location, double quantity) {
        this.activity = activity;
        this.location = location;
        this.quantity = quantity;
    }

    public Activity getActivity() {
        return activity;
    }

    public Location getLocation() {
        return location;
    }

    public double getQuantity() {
        return quantity;
    }

    public double getDuration() {

         double rawDuration=quantity / activity.getDailyProductivity();
         return Math.ceil(rawDuration);
    }

    public double getStartTime() {
        return startTime;
    }

    public void setStartTime(double startTime) {
        this.startTime = startTime;
        this.endTime = startTime + getDuration();
    }

    public double getEndTime() {
        return endTime;
    }

}
