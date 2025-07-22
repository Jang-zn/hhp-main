package kr.hhplus.be.server.api.docs.annotation;

import kr.hhplus.be.server.api.dto.response.OrderResponse;
import org.springframework.http.HttpStatus;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 주문 도메인 특화 API 문서화 어노테이션
 * 주문 관련 공통 에러 코드를 미리 정의
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OrderApiDocs {
    String summary();
    String description() default "";
    HttpStatus successStatus() default HttpStatus.OK;
    Class<?> responseType() default OrderResponse.class;
}