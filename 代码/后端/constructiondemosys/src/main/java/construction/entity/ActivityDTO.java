package construction.entity;

import java.util.Map;

public class ActivityDTO {
    public String id;
    public String name;
    public String trade;
    public int crew;
    public double dailyProd;
    // Key: LocationId, Value: Quantity
    public Map<String, Double> quantities;
}
