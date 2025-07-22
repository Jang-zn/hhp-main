package kr.hhplus.be.server.api.docs.schema;

import kr.hhplus.be.server.domain.exception.*;

import java.util.Map;

/**
 * 도메인별 에러 매핑 정의
 * 예외 클래스와 HTTP 상태 코드, 에러 코드를 중앙화하여 관리
 */
public class ErrorSchemas {

    /**
     * 잔액 도메인 에러 매핑
     */
    public static final Map<Class<? extends Exception>, ErrorInfo> BALANCE_ERRORS = Map.of(
            BalanceException.InsufficientBalance.class,
            new ErrorInfo(402, "INSUFFICIENT_BALANCE", "잔액이 부족합니다"),
            
            BalanceException.NotFound.class,
            new ErrorInfo(404, "BALANCE_NOT_FOUND", "잔액 정보를 찾을 수 없습니다"),
            
            BalanceException.InvalidAmount.class,
            new ErrorInfo(400, "INVALID_AMOUNT", "잘못된 금액입니다"),
            
            BalanceException.InvalidAmountRequired.class,
            new ErrorInfo(400, "INVALID_AMOUNT_REQUIRED", "충전 금액은 필수입니다"),
            
            BalanceException.InvalidAmountPositive.class,
            new ErrorInfo(400, "INVALID_AMOUNT_POSITIVE", "충전 금액은 0보다 커야 합니다")
    );

    /**
     * 쿠폰 도메인 에러 매핑
     */
    public static final Map<Class<? extends Exception>, ErrorInfo> COUPON_ERRORS = Map.of(
            CouponException.NotFound.class,
            new ErrorInfo(404, "COUPON_NOT_FOUND", "쿠폰을 찾을 수 없습니다"),
            
            CouponException.AlreadyIssued.class,
            new ErrorInfo(409, "COUPON_ALREADY_ISSUED", "이미 발급받은 쿠폰입니다"),
            
            CouponException.CouponNotYetStarted.class,
            new ErrorInfo(410, "COUPON_NOT_YET_STARTED", "쿠폰이 아직 시작되지 않았습니다"),
            
            CouponException.Expired.class,
            new ErrorInfo(410, "COUPON_EXPIRED", "쿠폰이 만료되었습니다"),
            
            CouponException.OutOfStock.class,
            new ErrorInfo(410, "COUPON_OUT_OF_STOCK", "쿠폰 재고가 소진되었습니다")
    );

    /**
     * 상품 도메인 에러 매핑
     */
    public static final Map<Class<? extends Exception>, ErrorInfo> PRODUCT_ERRORS = Map.of(
            ProductException.NotFound.class,
            new ErrorInfo(404, "PRODUCT_NOT_FOUND", "상품을 찾을 수 없습니다"),
            
            ProductException.OutOfStock.class,
            new ErrorInfo(409, "PRODUCT_OUT_OF_STOCK", "상품이 품절되었습니다"),
            
            ProductException.InvalidReservation.class,
            new ErrorInfo(400, "INVALID_RESERVATION", "잘못된 예약입니다"),
            
            ProductException.InvalidProductId.class,
            new ErrorInfo(400, "INVALID_PRODUCT_ID", "상품 ID는 필수입니다"),
            
            ProductException.InvalidDaysPositive.class,
            new ErrorInfo(400, "INVALID_DAYS_RANGE", "조회 기간은 0보다 커야 합니다")
    );

    /**
     * 주문 도메인 에러 매핑
     */
    public static final Map<Class<? extends Exception>, ErrorInfo> ORDER_ERRORS = Map.of(
            OrderException.NotFound.class,
            new ErrorInfo(404, "ORDER_NOT_FOUND", "주문을 찾을 수 없습니다"),
            
            OrderException.Unauthorized.class,
            new ErrorInfo(403, "ORDER_UNAUTHORIZED", "주문에 대한 접근 권한이 없습니다"),
            
            OrderException.AlreadyPaid.class,
            new ErrorInfo(409, "ORDER_ALREADY_PAID", "이미 결제된 주문입니다"),
            
            OrderException.EmptyItems.class,
            new ErrorInfo(400, "ORDER_EMPTY_ITEMS", "주문에는 최소 하나의 상품이 포함되어야 합니다")
    );

    /**
     * 사용자 도메인 에러 매핑
     */
    public static final Map<Class<? extends Exception>, ErrorInfo> USER_ERRORS = Map.of(
            UserException.NotFound.class,
            new ErrorInfo(404, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다"),
            
            UserException.InvalidUser.class,
            new ErrorInfo(400, "INVALID_USER", "잘못된 사용자입니다")
    );

    /**
     * 공통 에러 매핑
     */
    public static final Map<Class<? extends Exception>, ErrorInfo> COMMON_ERRORS = Map.of(
            CommonException.InvalidRequest.class,
            new ErrorInfo(400, "INVALID_REQUEST", "잘못된 요청입니다")
    );

    /**
     * 모든 에러 매핑을 통합한 맵
     */
    public static final Map<Class<? extends Exception>, ErrorInfo> ALL_ERRORS = Map.of(
            // Balance errors
            BalanceException.InsufficientBalance.class, BALANCE_ERRORS.get(BalanceException.InsufficientBalance.class),
            BalanceException.NotFound.class, BALANCE_ERRORS.get(BalanceException.NotFound.class),
            BalanceException.InvalidAmount.class, BALANCE_ERRORS.get(BalanceException.InvalidAmount.class),
            
            // Coupon errors
            CouponException.NotFound.class, COUPON_ERRORS.get(CouponException.NotFound.class),
            CouponException.AlreadyIssued.class, COUPON_ERRORS.get(CouponException.AlreadyIssued.class),
            CouponException.CouponNotYetStarted.class, COUPON_ERRORS.get(CouponException.CouponNotYetStarted.class),
            
            // Product errors
            ProductException.NotFound.class, PRODUCT_ERRORS.get(ProductException.NotFound.class),
            ProductException.OutOfStock.class, PRODUCT_ERRORS.get(ProductException.OutOfStock.class)
            
            // Note: Map.of는 최대 10개까지만 지원하므로, 더 많은 매핑이 필요한 경우 Map.ofEntries 사용
    );
}