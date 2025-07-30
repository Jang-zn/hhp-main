package kr.hhplus.be.server;

import java.math.BigDecimal;

/**
 * 테스트에서 사용하는 공통 상수들을 관리하는 클래스
 */
public final class TestConstants {
    
    // === 사용자 관련 상수 ===
    public static final String TEST_USER_NAME = "테스트 사용자";
    public static final Long TEST_USER_ID = 1L;
    
    // === 쿠폰 관련 상수 ===
    public static final String DISCOUNT_COUPON_CODE = "DISCOUNT10";
    public static final String EXPIRED_COUPON_CODE = "EXPIRED";
    public static final String OUT_OF_STOCK_COUPON_CODE = "OUTOFSTOCK";
    public static final String TEST_COUPON_CODE = "TEST-COUPON-001";
    public static final BigDecimal DEFAULT_DISCOUNT_RATE = new BigDecimal("10.0");
    
    // === 상품 관련 상수 ===
    public static final String TEST_PRODUCT_NAME = "Test Product";
    public static final BigDecimal DEFAULT_PRODUCT_PRICE = new BigDecimal("50000");
    public static final int DEFAULT_PRODUCT_STOCK = 100;
    
    // === 주문 관련 상수 ===
    public static final BigDecimal DEFAULT_ORDER_AMOUNT = new BigDecimal("100000");
    
    // === 잔액 관련 상수 ===
    public static final BigDecimal DEFAULT_BALANCE_AMOUNT = new BigDecimal("10000");
    public static final BigDecimal DEFAULT_CHARGE_AMOUNT = new BigDecimal("50000");
    
    // === 페이지네이션 관련 상수 ===
    public static final int DEFAULT_PAGE_LIMIT = 10;
    public static final int DEFAULT_PAGE_OFFSET = 0;
    public static final int DEFAULT_POPULAR_DAYS = 7;
    
    // private constructor to prevent instantiation
    private TestConstants() {
        throw new AssertionError("Cannot instantiate utility class");
    }
}