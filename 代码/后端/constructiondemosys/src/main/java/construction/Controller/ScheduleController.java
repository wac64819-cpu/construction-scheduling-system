package construction.Controller;

import construction.dto.ScheduleRequest;
import construction.dto.ScheduleResponse;
import org.springframework.web.bind.annotation.*;
import construction.Service.SchedulingService;

@RestController//返回的 Java 对象会被自动转成 JSON
@RequestMapping("/api/schedule")//定义访问路径前缀
@CrossOrigin//允许跨域访问，Vue 在 5173 或 3000 端口，Spring Boot 在 8080 端口


public class ScheduleController {

    private final SchedulingService service;//让 Controller 可以使用 Service 提供的业务功能。

    public ScheduleController(SchedulingService service) {
        this.service = service;
    }

    @PostMapping//给 Spring Boot 控制器方法贴的 “POST 请求专属门牌”
    public ScheduleResponse run(@RequestBody ScheduleRequest request) {
        return service.runScheduling(request);//将 HTTP 请求体（Request Body）中的 JSON/XML 等格式的数据，自动绑定到控制器方法的参数上，并完成数据类型转换。
    }
}