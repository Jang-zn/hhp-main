package kr.hhplus.be.server.api.docs.annotation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import kr.hhplus.be.server.api.docs.dto.ApiResponseDto;
import kr.hhplus.be.server.api.CommonResponse;
import org.springframework.http.HttpStatus;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 통합 API 문서화 어노테이션
 * 기존 @ApiSuccess, @ApiCreate를 대체하여 중앙화된 문서화 제공
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Operation
@ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "요청 성공",
                content = @Content(mediaType = "application/json",
                        schema = @Schema(implementation = CommonResponse.class))),
        @ApiResponse(responseCode = "400", description = "잘못된 요청",
                content = @Content(mediaType = "application/json",
                        schema = @Schema(implementation = ApiResponseDto.class))),
        @ApiResponse(responseCode = "500", description = "서버 오류",
                content = @Content(mediaType = "application/json",
                        schema = @Schema(implementation = ApiResponseDto.class)))
})
public @interface ApiDocs {
    String summary();
    String description() default "";
    HttpStatus successStatus() default HttpStatus.OK;
    Class<?> responseType() default Void.class;
    String[] errorCodes() default {};
}