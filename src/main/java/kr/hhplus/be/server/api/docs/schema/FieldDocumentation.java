package kr.hhplus.be.server.api.docs.schema;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 타입 안전한 필드 문서화 정보 컨테이너
 */
public record FieldDocumentation(List<FieldInfo> fields) {
    
    /**
     * 필드 정보를 담는 레코드
     */
    public record FieldInfo(
            String name,
            String description,
            String example,
            boolean required
    ) {
        /**
         * 필수 필드용 생성자
         */
        public FieldInfo(String name, String description, String example) {
            this(name, description, example, true);
        }
    }
    
    /**
     * 빌더 패턴으로 생성
     */
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private final List<FieldInfo> fields = new java.util.ArrayList<>();
        
        public Builder field(String name, String description, String example) {
            fields.add(new FieldInfo(name, description, example, true));
            return this;
        }
        
        public Builder field(String name, String description, String example, boolean required) {
            fields.add(new FieldInfo(name, description, example, required));
            return this;
        }
        
        public FieldDocumentation build() {
            return new FieldDocumentation(List.copyOf(fields));
        }
    }
    
}