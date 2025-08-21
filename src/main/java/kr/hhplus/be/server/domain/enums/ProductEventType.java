package kr.hhplus.be.server.domain.enums;

/**
 * 상품 이벤트 타입
 * 
 * Phase 4: 이벤트 기반 캐시 무효화 전략에서 사용
 */
public enum ProductEventType {
    /**
     * 상품 생성
     * - 개별 상품 캐시만 저장
     * - 다른 캐시 무효화는 하지 않음
     */
    CREATED,
    
    /**
     * 상품 전체 정보 수정 (이름, 가격, 재고 등)
     * - 모든 관련 캐시 무효화 필요
     * - 주문/쿠폰 도메인에 영향
     */
    UPDATED,
    
    /**
     * 재고만 수정
     * - 개별 상품 캐시 갱신
     * - 주문 관련 캐시만 무효화
     * - 목록 캐시는 유지 (성능 최적화)
     */
    STOCK_UPDATED,
    
    /**
     * 상품 삭제
     * - 모든 관련 캐시 무효화
     * - 주문/쿠폰/랭킹 모든 도메인 영향
     */
    DELETED
}