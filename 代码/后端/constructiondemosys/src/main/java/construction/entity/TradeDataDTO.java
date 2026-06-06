package construction.entity;

public class TradeDataDTO {
    public String id;
    public String name;
    public int capacity;
    public double standardRate; // ✅ 修改：统一叫 standardRate，不要叫 costPerHour

    public TradeDataDTO() {}

    public TradeDataDTO(String id, String name, int capacity, double standardRate) {
        this.id = id;
        this.name = name;
        this.capacity = capacity;
        this.standardRate = standardRate;
    }
}