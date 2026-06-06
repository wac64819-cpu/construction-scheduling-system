package construction.constraint;

public class TradeData {

    private final String tradeName;
    private final int availableWorkers;
    public double standardRate;

    public TradeData(String tradeName, int availableWorkers,double standardRate) {
        if (availableWorkers <= 0) {
            throw new IllegalArgumentException(
                    "Workers must be positive for trade: " + tradeName);
        }
        this.tradeName = tradeName;
        this.availableWorkers = availableWorkers;
        this.standardRate = standardRate;

    }

    public String getTradeName() {
        return tradeName;
    }

    public int getAvailableWorkers() {
        return availableWorkers;
    }

    public double getStandardRate() {return standardRate;}
}