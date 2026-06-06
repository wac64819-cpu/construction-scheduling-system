package construction.dto;

public class ConstraintSummaryDTO {
    private String type; // e.g., "Logic", "Resource", "Location"
    private String description;

    public ConstraintSummaryDTO(String type, String description) {
        this.type = type;
        this.description = description;
    }
    public ConstraintSummaryDTO() {}
    public String getType() { return type; }
    public String getDescription() { return description; }
}