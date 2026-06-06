package construction.dto;

import java.util.List;

public class SolutionDTO {

    // 原有目标
    private double makespan;
    private double peakWorkforce;

    // ✅ 新增目标
    private double resourceFluctuation; // 资源波动
    private double workContinuity;      // 闲置天数
    private double totalCost;           // 总成本

    private List<TaskScheduleDTO> tasks;

    public SolutionDTO(double makespan,
                       double peakWorkforce,
                       double resourceFluctuation, // ✅ 新增
                       double workContinuity,      // ✅ 新增
                       double totalCost,           // ✅ 新增
                       List<TaskScheduleDTO> tasks) {
        this.makespan = makespan;
        this.peakWorkforce = peakWorkforce;
        this.resourceFluctuation = resourceFluctuation;
        this.workContinuity = workContinuity;
        this.totalCost = totalCost;
        this.tasks = tasks;
    }
    // ✅ 必须加上无参构造函数！
    public SolutionDTO() {}
    public double getMakespan() { return makespan; }
    public double getPeakWorkforce() { return peakWorkforce; }

    // ✅ 新增 Getter
    public double getResourceFluctuation() { return resourceFluctuation; }
    public double getWorkContinuity() { return workContinuity; }
    public double getTotalCost() { return totalCost; }

    public List<TaskScheduleDTO> getTasks() { return tasks; }
}