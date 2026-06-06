package construction.dto;

public class TaskScheduleDTO {

    private String activity;
    private String location;
    private double start;
    private double end;

    public TaskScheduleDTO(String activity,
                           String location,
                           double start,
                           double end) {
        this.activity = activity;
        this.location = location;
        this.start = start;
        this.end = end;
    }
    // ✅ 必须加上无参构造函数！
    public TaskScheduleDTO() {}
    public String getActivity() { return activity; }
    public String getLocation() { return location; }
    public double getStart() { return start; }
    public double getEnd() { return end; }
}