package construction.dto;

import java.util.List;

import construction.model.Location;
import java.util.List;

public class ProjectInfoDTO {
    private List<ActivitySummaryDTO> activities;
    private List<ConstraintSummaryDTO> constraints;
    // 新增：所有的地点列（用于生成表头）
    private List<Location> locationColumns;

    public ProjectInfoDTO(List<ActivitySummaryDTO> activities, List<ConstraintSummaryDTO> constraints, List<Location> locationColumns) {
        this.activities = activities;
        this.constraints = constraints;
        this.locationColumns = locationColumns;
    }
    public ProjectInfoDTO() {}
    public List<ActivitySummaryDTO> getActivities() { return activities; }
    public List<ConstraintSummaryDTO> getConstraints() { return constraints; }
    public List<Location> getLocationColumns() { return locationColumns; }
}