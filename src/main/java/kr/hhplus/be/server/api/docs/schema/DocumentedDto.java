package kr.hhplus.be.server.api.docs.schema;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * DTO 문서화 표준 인터페이스
 * 모든 DTO는 이 인터페이스를 구현하여 자동 문서화를 지원
 */
public interface DocumentedDto {
    
    /**
     * 타입 안전한 필드 문서화 정보를 반환
     * JSON 직렬화에서 제외하여 API 요청/응답에 영향을 주지 않도록 함
     * @return 타입 안전한 필드 문서화 정보
     */
    @JsonIgnore
    FieldDocumentation getFieldDocumentation();
}