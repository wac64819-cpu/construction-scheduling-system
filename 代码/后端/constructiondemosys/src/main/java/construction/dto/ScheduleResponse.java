package construction.dto;
import java.util.List;
public class ScheduleResponse {
    private List<SolutionDTO> solutions;
    private ProjectInfoDTO projectInfo;
    // 构造函数
    public ScheduleResponse(List<SolutionDTO> solutions, ProjectInfoDTO projectInfoDTO) {
        this.solutions = solutions;
        // 【修复点 1】必须把参数赋值给成员变量！
        this.projectInfo = projectInfoDTO;
    }
    // ✅ 必须加上无参构造函数！
    public ScheduleResponse() {}
    public List<SolutionDTO> getSolutions() {
        return solutions;
    }
    // 【修复点 2】必须添加 Getter 方法，否则前端收不到数据！
    public ProjectInfoDTO getProjectInfo() {
        return projectInfo;
    }
}