package kr.hhplus.be.server.api;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 성공 응답 자동 래핑 클래스
 * 
 * 컨트롤러에서 반환된 성공 응답을 자동으로 CommonResponse로 감싸는 역할을 한다.
 * Spring의 ResponseBodyAdvice를 구현하여 HTTP 응답 본문이 작성되기 전에 개입한다.
 * 
 * 동작 과정:
 * 1. 컨트롤러에서 UserDto 반환
 * 2. SuccessResponseAdvice가 개입
 * 3. CommonResponse.success(UserDto)로 래핑
 * 4. 클라이언트는 표준화된 응답 받음
 * 
 * 적용 범위: kr.hhplus.be.server.api.controller 패키지의 모든 컨트롤러
 * 제외 대상: ResponseEntity를 반환하는 메서드 (주로 예외 처리 핸들러)
 */
@RestControllerAdvice(basePackages = "kr.hhplus.be.server.api.controller")
public class SuccessResponseAdvice implements ResponseBodyAdvice<Object> {

    /**
     * 이 Advice를 적용할지 판단하는 메서드
     * 
     * @param returnType 메서드의 반환 타입 정보
     * @param converterType HTTP 메시지 컨버터 타입
     * @return true면 beforeBodyWrite 메서드 실행, false면 스킵
     */
    @Override
    public boolean supports(MethodParameter returnType, Class converterType) {
        // ResponseEntity를 반환하는 예외 처리 핸들러는 이미 완성된 응답이므로 제외
        // GlobalExceptionHandler의 메서드들은 ResponseEntity<CommonResponse>를 반환하므로 중복 래핑 방지
        return !returnType.getParameterType().equals(ResponseEntity.class);
    }

    /**
     * HTTP 응답 본문이 작성되기 전에 실행되는 메서드
     * 컨트롤러에서 반환된 원본 데이터를 CommonResponse로 래핑한다.
     * 
     * @param body 컨트롤러에서 반환된 원본 응답 객체
     * @param returnType 메서드의 반환 타입 정보
     * @param selectedContentType 선택된 Content-Type
     * @param selectedConverterType 선택된 메시지 컨버터
     * @param request HTTP 요청 객체
     * @param response HTTP 응답 객체
     * @return CommonResponse로 래핑된 응답 객체
     */
    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {
        // void 메서드인 경우 (예: 잔액 충전) - 데이터 없이 성공 메시지만
        if (returnType.getParameterType() == void.class) {
            return CommonResponse.success(); // { "success": true, "message": "요청이 성공했습니다", "data": null }
        }
        // 데이터가 있는 경우 (예: 상품 목록 조회) - 원본 데이터를 data 필드에 포함
        return CommonResponse.success(body); // { "success": true, "message": "요청이 성공했습니다", "data": body }
    }
} 