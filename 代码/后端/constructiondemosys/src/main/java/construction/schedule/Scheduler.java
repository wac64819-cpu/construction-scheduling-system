package construction.schedule;

import construction.constraint.ActivityConstraint;
import construction.constraint.LocationPrecedenceConstraint;
import construction.constraint.PrecedenceConstraint;
import construction.model.Task;
import org.uma.jmetal.solution.doublesolution.DoubleSolution;
import java.util.List;
import java.util.Map;

public interface Scheduler {

    ScheduleResult generateSchedule(DoubleSolution solution,
                                    List<Task> tasks,
                                    List<PrecedenceConstraint> precedences,
                                    List<ActivityConstraint> activityConstraints,
                                    List<LocationPrecedenceConstraint> locationPrecedences,
                                    Map<String, Integer> tradeCapacities,
                                    double maxOutsourcingRatio); // ✅ 新增参数

}