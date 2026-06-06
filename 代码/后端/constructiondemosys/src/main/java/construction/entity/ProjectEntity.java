package construction.entity;

import construction.dto.ScheduleResponse;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "projects")
public class ProjectEntity {
    @Id
    public String id;
    public String name;
    public String createdAt;

    public ProjectModelData model;
    public ProjectConstraints constraints = new ProjectConstraints();
    public List<TradeDataDTO> tradeData = new ArrayList<>();

    // ✅ 新增：嵌入 Settings，并给默认值
    public ProjectSettings settings = new ProjectSettings();

    // ✅ 新增：保存上一次运行的调度结果
    public ScheduleResponse latestSchedule = null;
}