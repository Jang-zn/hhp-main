package kr.hhplus.be.server.api;

/**
 * API 에러 코드 및 메시지 관리 Enum
 * 
 * 모든 에러 코드를 중앙에서 관리하여 일관된 에러 응답을 제공한다.
 * 에러 코드는 도메인별로 분류하여 관리한다.
 * 
 * 코드 패턴:
 * - SUCCESS: S001
 * - 사용자 관련: U001~U999
 * - 잔액 관련: B001~B999  
 * - 상품 관련: P001~P999
 * - 주문 관련: O001~O999
 * - 쿠폰 관련: C001~C999
 * - 시스템 에러: E001~E999
 */
public enum ErrorCode {
    
    // === 성공 응답 ===
    SUCCESS("S001", "요청이 성공적으로 처리되었습니다."),
    
    // === 사용자 관련 에러 (U001~U999) ===
    USER_NOT_FOUND("U001", "사용자를 찾을 수 없습니다."),
    INVALID_USER_ID("U002", "유효하지 않은 사용자 ID입니다."),
    USER_ALREADY_EXISTS("U003", "이미 존재하는 사용자입니다."),
    UNAUTHORIZED("U401", "인증되지 않은 사용자입니다."),
    FORBIDDEN("U403", "접근 권한이 없습니다."),
    
    // === 잔액 관련 에러 (B001~B999) ===
    BALANCE_NOT_FOUND("B001", "잔액 정보를 찾을 수 없습니다."),
    INSUFFICIENT_BALANCE("B002", "잔액이 부족합니다."),
    INVALID_AMOUNT("B003", "유효하지 않은 금액입니다."),
    NEGATIVE_AMOUNT("B004", "금액은 0보다 커야 합니다."),
    AMOUNT_TOO_LARGE("B005", "금액이 너무 큽니다."),
    
    // === 상품 관련 에러 (P001~P999) ===
    PRODUCT_NOT_FOUND("P001", "상품을 찾을 수 없습니다."),
    PRODUCT_OUT_OF_STOCK("P002", "상품 재고가 부족합니다."),
    INVALID_PRODUCT_ID("P003", "유효하지 않은 상품 ID입니다."),
    PRODUCT_PRICE_CHANGED("P004", "상품 가격이 변경되었습니다."),
    INVALID_RESERVATION("P005", "유효하지 않은 예약입니다."),
    
    // === 주문 관련 에러 (O001~O999) ===
    ORDER_NOT_FOUND("O001", "주문을 찾을 수 없습니다."),
    INVALID_ORDER_STATUS("O002", "유효하지 않은 주문 상태입니다."),
    ORDER_ALREADY_PAID("O003", "이미 결제된 주문입니다."),
    ORDER_EXPIRED("O004", "주문이 만료되었습니다."),
    INVALID_ORDER_ITEMS("O005", "유효하지 않은 주문 상품입니다."),
    ORDER_AMOUNT_MISMATCH("O006", "주문 금액이 일치하지 않습니다."),
    
    // === 쿠폰 관련 에러 (C001~C999) ===
    COUPON_NOT_FOUND("C001", "쿠폰을 찾을 수 없습니다."),
    COUPON_EXPIRED("C002", "만료된 쿠폰입니다."),
    COUPON_ALREADY_USED("C003", "이미 사용된 쿠폰입니다."),
    COUPON_NOT_YET_STARTED("C004", "아직 사용할 수 없는 쿠폰입니다."),
    COUPON_ALREADY_ISSUED("C005", "이미 발급된 쿠폰입니다."),
    COUPON_ISSUE_LIMIT_EXCEEDED("C006", "쿠폰 발급 한도를 초과했습니다."),
    
    // === 입력 검증 에러 (V001~V999) ===
    INVALID_INPUT("V001", "유효하지 않은 입력입니다."),
    MISSING_REQUIRED_FIELD("V002", "필수 필드가 누락되었습니다."),
    INVALID_FORMAT("V003", "잘못된 형식입니다."),
    VALUE_OUT_OF_RANGE("V004", "값이 허용 범위를 벗어났습니다."),
    
    // === 시스템 에러 (E001~E999) ===
    INTERNAL_SERVER_ERROR("E500", "서버 내부 오류가 발생했습니다."),
    DATABASE_ERROR("E501", "데이터베이스 오류가 발생했습니다."),
    EXTERNAL_API_ERROR("E502", "외부 API 호출 중 오류가 발생했습니다."),
    CONCURRENCY_ERROR("E503", "동시성 처리 중 오류가 발생했습니다."),
    LOCK_ACQUISITION_FAILED("E504", "락 획득에 실패했습니다."),
    
    // === HTTP 상태 관련 에러 ===
    BAD_REQUEST("E400", "잘못된 요청입니다."),
    NOT_FOUND("E404", "요청한 리소스를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED("E405", "허용되지 않은 HTTP 메서드입니다."),
    UNSUPPORTED_MEDIA_TYPE("E415", "지원하지 않는 미디어 타입입니다.");
    
    private final String code;
    private final String message;
    
    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getMessage() {
        return message;
    }
    
    /**
     * 에러 코드로 ErrorCode 찾기
     * @param code 에러 코드
     * @return ErrorCode 또는 null
     */
    public static ErrorCode findByCode(String code) {
        for (ErrorCode errorCode : values()) {
            if (errorCode.getCode().equals(code)) {
                return errorCode;
            }
        }
        return null;
    }
    
    /**
     * 도메인별 에러 코드인지 확인
     * @param domain 도메인 (U, B, P, O, C, V, E)
     * @return 해당 도메인의 에러 코드인지 여부
     */
    public boolean isDomainError(String domain) {
        return this.code.startsWith(domain);
    }
    
    /**
     * 시스템 에러인지 확인 (E로 시작하는 에러)
     * @return 시스템 에러 여부
     */
    public boolean isSystemError() {
        return isDomainError("E");
    }
    
    /**
     * 클라이언트 에러인지 확인 (4xx 계열)
     * @return 클라이언트 에러 여부
     */
    public boolean isClientError() {
        return code.equals("E400") || code.equals("E404") || code.equals("E405") || code.equals("E415") ||
               isDomainError("U") || isDomainError("V");
    }
}