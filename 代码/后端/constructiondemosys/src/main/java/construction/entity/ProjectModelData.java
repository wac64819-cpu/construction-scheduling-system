package construction.entity;

import java.util.List;
import java.util.Map;

public class ProjectModelData {
    public List<String> hierarchyLevels; // ["CP", "SZ", "Zone"]
    public List<LocationNode> locationTree; // 树形结构
    public List<ActivityDTO> activities;    // 任务列表
}