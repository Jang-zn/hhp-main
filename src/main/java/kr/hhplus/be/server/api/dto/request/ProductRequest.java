package kr.hhplus.be.server.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import kr.hhplus.be.server.api.docs.schema.DocumentedDto;
import kr.hhplus.be.server.api.ErrorCode;

import kr.hhplus.be.server.api.docs.schema.FieldDocumentation;

@Schema(description = "상품 관련 요청")
public class ProductRequest implements DocumentedDto {
    
    @Schema(description = "페이지 크기", example = "10", defaultValue = "10")
    @Positive
    @Max(value = 100)
    private int limit = 10;
    
    @Schema(description = "페이지 오프셋", example = "0", defaultValue = "0")
    @PositiveOrZero
    private int offset = 0;
    
    @Schema(description = "조회 기간(일)", example = "3", defaultValue = "3")
    @Positive
    @Max(value = 30)
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

    @Override
    public FieldDocumentation getFieldDocumentation() {
        return FieldDocumentation.builder()
                .field("limit", "페이지 크기", "10", false)
                .field("offset", "페이지 오프셋", "0", false)
                .field("days", "조회 기간(일)", "3", false)
                .build();
    }
    
}