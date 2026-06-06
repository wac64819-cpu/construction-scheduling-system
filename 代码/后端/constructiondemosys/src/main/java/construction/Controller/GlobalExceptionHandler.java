package construction.Controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器，保证前端接收到结构化 JSON
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 处理逻辑死锁异常（前序-后序循环）
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException e) {
        Map<String, Object> body = new HashMap<>();

        // 匹配具体异常类型，生成 code
        if (e.getMessage() != null && e.getMessage().contains("Precedence graph has cycle")) {
            body.put("code", "LOGICAL_DEADLOCK");
        } else if (e.getMessage() != null && e.getMessage().contains("negative cycle")) {
            body.put("code", "TEMPORAL_INCONSISTENCY");
        } else {
            body.put("code", "CONSTRAINT_ERROR");
        }

        // 原始异常信息也返回给前端
        body.put("message", e.getMessage());

        // 统一使用 400 Bad Request
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // 可继续添加其他异常处理
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOtherExceptions(Exception e) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", "SYSTEM_ERROR");
        body.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}