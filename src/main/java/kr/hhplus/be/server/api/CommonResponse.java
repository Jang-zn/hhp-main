package kr.hhplus.be.server.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;

public class CommonResponse<T> {
    private final boolean success;
    private final String message;
    private final T data;
    private final LocalDateTime timestamp;
    
    private CommonResponse(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.timestamp = LocalDateTime.now();
    }
    
    // 성공 응답 (데이터 포함)
    public static <T> ResponseEntity<CommonResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(new CommonResponse<>(true, message, data));
    }
    
    // 성공 응답 (데이터 없음)
    public static ResponseEntity<CommonResponse<Object>> ok(String message) {
        return ResponseEntity.ok(new CommonResponse<>(true, message, null));
    }
    
    // 성공 응답 (기본 메시지)
    public static <T> ResponseEntity<CommonResponse<T>> ok(T data) {
        return ResponseEntity.ok(new CommonResponse<>(true, "성공", data));
    }
    
    // 실패 응답 (HTTP Status Code와 함께)
    public static ResponseEntity<CommonResponse<Object>> fail(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new CommonResponse<>(false, message, null));
    }
    
    // 실패 응답 (기본 BAD_REQUEST)
    public static ResponseEntity<CommonResponse<Object>> fail(String message) {
        return ResponseEntity.badRequest().body(new CommonResponse<>(false, message, null));
    }
    
    // 생성 응답 (201 Created)
    public static <T> ResponseEntity<CommonResponse<T>> created(String message, T data) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new CommonResponse<>(true, message, data));
    }
    
    // Getters
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public T getData() { return data; }
    public LocalDateTime getTimestamp() { return timestamp; }
} 