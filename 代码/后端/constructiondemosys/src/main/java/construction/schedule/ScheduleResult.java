package construction.schedule;

public class ScheduleResult {

    private final double makespan;
    private final double peakWorkforce;

    public ScheduleResult(double makespan, double peakWorkforce) {
        this.makespan = makespan;
        this.peakWorkforce = peakWorkforce;
    }

    public double getMakespan() {
        return makespan;
    }

    public double getPeakWorkforce() {
        return peakWorkforce;
    }

}
