package construction.dto;

import java.util.Map;



import java.util.Map;

public class ActivitySummaryDTO {
    private int id;
    private String activityName;
    private String trade;
    private int crewSize;          // 人数
    private double dailyProductivity; // 产量 (Quantity)
    private double totalQuantity;
    private Map<String, Double> quantities;

    public ActivitySummaryDTO(int id, String activityName, String trade, int crewSize, double dailyProductivity, double totalQuantity, Map<String, Double> quantities) {
        this.id = id;
        this.activityName = activityName;
        this.trade = trade;
        this.crewSize = crewSize;
        this.dailyProductivity = dailyProductivity;
        this.totalQuantity = totalQuantity;
        this.quantities = quantities;
    }
    public ActivitySummaryDTO() {}
    public int getId() { return id; }
    public String getActivityName() { return activityName; }
    public String getTrade() { return trade; }
    public int getCrewSize() { return crewSize; }
    public double getDailyProductivity() { return dailyProductivity; }
    public double getTotalQuantity() { return totalQuantity; }
    public Map<String, Double> getQuantities() { return quantities; }
}