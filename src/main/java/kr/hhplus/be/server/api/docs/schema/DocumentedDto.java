package kr.hhplus.be.server.api.docs.schema;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Map;

/**
 * DTO 문서화 표준 인터페이스
 * 모든 DTO는 이 인터페이스를 구현하여 자동 문서화를 지원
 */
public interface DocumentedDto {
    
    /**
     * 필드별 문서화 정보를 반환
     * JSON 직렬화에서 제외하여 API 요청/응답에 영향을 주지 않도록 함
     * @return 필드명과 SchemaInfo의 매핑
     */
    @JsonIgnore
    Map<String, SchemaInfo> getFieldDocumentation();
    
    /**
     * 스키마 정보를 담는 레코드
     */
    record SchemaInfo(
            String description,
            String example,
            boolean required
    ) {
        /**
         * 필수 필드용 생성자
         */
        public SchemaInfo(String description, String example) {
            this(description, example, true);
        }
    }
}