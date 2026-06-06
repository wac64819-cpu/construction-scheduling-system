package construction.dto;

public class ScheduleRequest {

    private String projectId; // 新增：用于数据库查询
    private int maxEvaluations = 500;

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public int getMaxEvaluations() {
        return maxEvaluations;
    }

    public void setMaxEvaluations(int maxEvaluations) {
        this.maxEvaluations = maxEvaluations;
    }
}