package construction.Controller;

import construction.Service.ExcelParserService;
import construction.dto.EmptyProjectRequest;
import construction.entity.ProjectEntity;
import construction.entity.ProjectConstraints; // 引入 constraints 包装类
import construction.entity.ProjectModelData;
import construction.entity.ProjectSettings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/projects")
@CrossOrigin(origins = "*")
public class ProjectController {

    @Autowired
    private ExcelParserService parserService;

    @Autowired
    private MongoTemplate mongoTemplate;

    // 1. 新建 Project
    @PostMapping
    public ProjectEntity createProject(@RequestParam("name") String name,
                                       @RequestParam("file") MultipartFile file) {
        checkProjectNameUnique(name, null);

        try {
            ProjectEntity project = new ProjectEntity();
            project.name = name;
            project.createdAt = LocalDate.now().toString();

            // 解析 Excel (Model)
            project.model = parserService.parse(file);

            // 初始化空的约束和 TradeData，等待用户在前端配置
            project.constraints = new ProjectConstraints();
            project.tradeData = new ArrayList<>();

            return mongoTemplate.save(project);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Excel parsing failed: " + e.getMessage());
        }
    }

    @PostMapping("/empty")
    public ProjectEntity createEmptyProject(@RequestBody EmptyProjectRequest request) {

        // 1. 校验重名
        checkProjectNameUnique(request.getName(), null);

        // 2. 初始化项目实体
        ProjectEntity project = new ProjectEntity();
        project.name = request.getName();
        project.createdAt = LocalDate.now().toString();

        // 3. 初始化空的 Model 数据，但写入前端传来的 hierarchyLevels
        ProjectModelData emptyModel = new ProjectModelData();
        emptyModel.hierarchyLevels = request.getHierarchyLevels();
        emptyModel.locationTree = new ArrayList<>(); // 空的树，等待在 ModelView 中添加
        emptyModel.activities = new ArrayList<>();   // 空的任务列表

        project.model = emptyModel;

        // 4. 初始化空约束和配置
        project.constraints = new ProjectConstraints();
        project.tradeData = new ArrayList<>();
        project.settings = new ProjectSettings();

        // 5. 保存入库
        return mongoTemplate.save(project);
    }

    // 2. 获取单个 Project
    @GetMapping("/{id}")
    public ProjectEntity getProject(@PathVariable String id) {
        return mongoTemplate.findById(id, ProjectEntity.class);
    }

    // 3. 保存/更新 Project
    @PutMapping("/{id}")
    public ProjectEntity updateProject(@PathVariable String id, @RequestBody ProjectEntity updatedData) {
        checkProjectNameUnique(updatedData.name, id);
        updatedData.id = id;
        // ✅ 核心逻辑：一旦修改任何参数，作废之前的调度结果
        updatedData.latestSchedule = null;
        return mongoTemplate.save(updatedData);
    }

    // 4. 获取列表
    @GetMapping
    public List<ProjectEntity> listProjects() {
        return mongoTemplate.findAll(ProjectEntity.class);
    }

    // 5. 删除 Project
    @DeleteMapping("/{id}")
    public void deleteProject(@PathVariable String id) {
        mongoTemplate.remove(new Query(Criteria.where("_id").is(id)), ProjectEntity.class);
    }

    private void checkProjectNameUnique(String name, String currentId) {
        Criteria criteria = Criteria.where("name").is(name);
        if (currentId != null) {
            criteria.and("id").ne(currentId);
        }
        if (mongoTemplate.exists(new Query(criteria), ProjectEntity.class)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Project name '" + name + "' already exists.");
        }
    }
}