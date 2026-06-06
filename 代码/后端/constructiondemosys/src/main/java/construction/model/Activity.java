package construction.model;

public class Activity {
    private final String name;
    private final String trade;          // 施工队 / 工种
    private final int crewSize;          // 固定
    private final double dailyProductivity;

    public Activity(String name,
                    String trade,
                    int crewSize,
                    double dailyProductivity) {
        this.name = name;
        this.trade = trade;
        this.crewSize = crewSize;
        this.dailyProductivity = dailyProductivity;
    }

    public String getName() {
        return name;
    }

    public String getTrade() {
        return trade;
    }

    public int getCrewSize() {
        return crewSize;
    }

    public double getDailyProductivity() {
        return dailyProductivity;
    }

}
