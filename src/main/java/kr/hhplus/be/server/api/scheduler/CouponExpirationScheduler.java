package kr.hhplus.be.server.api.scheduler;

import kr.hhplus.be.server.domain.usecase.coupon.ExpireCouponsUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 쿠폰 만료 처리 스케줄러
 * 주기적으로 만료된 쿠폰들의 상태를 업데이트합니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CouponExpirationScheduler {
    
    private final ExpireCouponsUseCase expireCouponsUseCase;
    
    /**
     * 매 시간 정각에 만료된 쿠폰들을 처리합니다.
     * cron 표현식: 초(0) 분(0) 시(*) 일(*) 월(*) 요일(*)
     */
    @Scheduled(cron = "0 0 * * * *")
    public void expireCoupons() {
        log.info("쿠폰 만료 처리 배치 시작");
        
        try {
            expireCouponsUseCase.execute();
            log.info("쿠폰 만료 처리 배치 완료");
        } catch (Exception e) {
            log.error("쿠폰 만료 처리 배치 실행 중 오류 발생", e);
        }
    }
}