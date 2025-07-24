package kr.hhplus.be.server.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.hhplus.be.server.api.docs.schema.DocumentedDto;
import kr.hhplus.be.server.api.ErrorCode;

import java.util.Map;

@Schema(description = "상품 관련 요청")
public class ProductRequest implements DocumentedDto {
    
    @Schema(description = "페이지 크기", example = "10", defaultValue = "10")
    private int limit = 10;
    
    @Schema(description = "페이지 오프셋", example = "0", defaultValue = "0")
    private int offset = 0;
    
    @Schema(description = "조회 기간(일)", example = "3", defaultValue = "3")
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
    public Map<String, SchemaInfo> getFieldDocumentation() {
        return Map.of(
                "limit", new SchemaInfo("페이지 크기", "10", false),
                "offset", new SchemaInfo("페이지 오프셋", "0", false),
                "days", new SchemaInfo("조회 기간(일)", "3", false)
        );
    }
    
    /**
     * 요청 데이터 검증
     * @throws IllegalArgumentException 검증 실패 시
     */
    public void validate() {
        if (limit <= 0) {
            throw new IllegalArgumentException(ErrorCode.INVALID_INPUT.getMessage());
        }
        if (limit > 100) {
            throw new IllegalArgumentException(ErrorCode.VALUE_OUT_OF_RANGE.getMessage());
        }
        if (offset < 0) {
            throw new IllegalArgumentException(ErrorCode.INVALID_INPUT.getMessage());
        }
        if (days <= 0) {
            throw new IllegalArgumentException(ErrorCode.INVALID_INPUT.getMessage());
        }
        if (days > 30) {
            throw new IllegalArgumentException(ErrorCode.VALUE_OUT_OF_RANGE.getMessage());
        }
    }
}