package kr.hhplus.be.server.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

@Schema(description = "상품 관련 요청")
public class ProductRequest {
    
    @Schema(description = "페이지 크기", example = "10", defaultValue = "10")
    @Positive(message = "limit은 양수여야 합니다")
    @Max(value = 100, message = "limit은 100 이하여야 합니다")
    private int limit = 10;
    
    @Schema(description = "페이지 오프셋", example = "0", defaultValue = "0")
    @PositiveOrZero(message = "offset은 0 이상이어야 합니다")
    private int offset = 0;
    
    @Schema(description = "조회 기간(일)", example = "3", defaultValue = "3")
    @Positive(message = "조회 기간(일)은 양수여야 합니다")
    @Max(value = 30, message = "조회 기간은 30일 이하여야 합니다")
    private int days = 3;

    // 기본 생성자
    public ProductRequest() {}

    // 생성자들
    public ProductRequest(int limit, int offset) {
        this.limit = limit;
        this.offset = offset;
    }
    
    public ProductRequest(int days) {
        this.days = days;
    }

    // Getters and Setters
    public int getLimit() { return limit; }
    public void setLimit(int limit) { this.limit = limit; }
    public int getOffset() { return offset; }
    public void setOffset(int offset) { this.offset = offset; }
    public int getDays() { return days; }
    public void setDays(int days) { this.days = days; }
}