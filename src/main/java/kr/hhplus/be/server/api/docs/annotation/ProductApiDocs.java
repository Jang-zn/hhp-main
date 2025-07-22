package kr.hhplus.be.server.api.docs.annotation;

import kr.hhplus.be.server.api.dto.response.ProductResponse;
import org.springframework.http.HttpStatus;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 상품 도메인 특화 API 문서화 어노테이션
 * 상품 관련 공통 에러 코드를 미리 정의
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ProductApiDocs {
    String summary();
    String description() default "";
    HttpStatus successStatus() default HttpStatus.OK;
    Class<?> responseType() default ProductResponse.class;
}