package kr.hhplus.be.server.api;

public class CommonResponse<T> {
    private boolean success;
    private String message;
    private T data;
    
    private CommonResponse(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }
    
    public static <T> CommonResponse<T> success(T data) {
        return new CommonResponse<>(true, "성공", data);
    }
    
    public static <T> CommonResponse<T> success(String message, T data) {
        return new CommonResponse<>(true, message, data);
    }
    
    public static <T> CommonResponse<T> error(String message) {
        return new CommonResponse<>(false, message, null);
    }
    
    // Getters
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public T getData() { return data; }
} 