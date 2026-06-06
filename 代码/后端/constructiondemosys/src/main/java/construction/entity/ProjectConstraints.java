package construction.entity;

import construction.constraint.*; // 引用你的约束逻辑类
import construction.dto.ActivityConstraintDTO;
import construction.dto.LocationPrecedenceConstraintDTO;
import construction.dto.PrecedenceConstraintDTO;

import java.util.List;
import java.util.ArrayList;

public class ProjectConstraints {
    public List<PrecedenceConstraintDTO> precedenceConstraints = new ArrayList<>();
    public List<ActivityConstraintDTO> activityConstraints = new ArrayList<>();
    public List<LocationPrecedenceConstraintDTO> locationConstraints = new ArrayList<>();
}