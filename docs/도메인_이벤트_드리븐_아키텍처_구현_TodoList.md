# 도메인 이벤트 드리븐 아키텍처 구현 TodoList

> **목표**: 현재의 기술적 이벤트 처리 방식을 진정한 도메인 이벤트 드리븐 아키텍처로 전환
> 
> **핵심 전략**: ApplicationEventPublisher 완전 제거 → MessagingPort 단일 진입점 → 도메인별 독립적 이벤트 처리

---

## 🎯 **Phase 1: 현재 상황 분석 및 기반 작업** 

### 1.1 현재 아키텍처 문제점 분석
- [x] ✅ **현재 구현 방식 분석 완료**
  - 기술적 이벤트 패턴 (캐시 동기화 위주)
  - ApplicationEventPublisher 직접 사용
  - 도메인 간 강한 결합 (Payment → Balance → Product 직접 호출)

- [x] ✅ **Producer-Consumer 인프라 완료 확인**
  - RedisEventAdapter (Producer) 완료
  - RedisEventConsumer (Consumer) 완료  
  - EventLog 상태 추적 완료
  - 테스트 안정성 100% 달성

### 1.2 목표 아키텍처 설계
- [x] ✅ **도메인별 EventHandler 구현 완료**
  - PaymentEventHandler (결제 도메인) ✅
  - BalanceEventHandler (잔액 도메인) ✅
  - ProductEventHandler (상품 도메인) ✅
  - OrderEventHandler (주문 도메인) ✅
  - CouponEventHandler (쿠폰 도메인) ✅

- [x] ✅ **도메인 이벤트 정의 완료**
  - PaymentCompletedEvent ✅
  - OrderCompletedEvent ✅
  - ProductUpdatedEvent ✅
  - CouponIssuedEvent ✅
  - BalanceUpdatedEvent ✅

---

## 🔄 **Phase 2: Router + Handler 패턴 구현 완료** ✅

### 2.1 폴더 구조 및 네이밍 정리
- [x] ✅ **/adapter/event/handler/ 폴더 생성**
- [x] ✅ **RedisEventConsumer → RedisEventRouter 완료**
  - 라우팅 책임만 담당하도록 역할 명확화
  - 기존 RedisEventConsumer 삭제 완료

### 2.2 도메인별 EventHandler 구현 완료
- [x] ✅ **PaymentEventHandler 구현**
  - 결제 완료 후 사후 처리 담당
- [x] ✅ **ProductEventHandler 구현** 
  - 상품 변경 시 캐시 무효화 처리
- [x] ✅ **OrderEventHandler 구현**
  - 주문 완료 시 랭킹 업데이트
- [x] ✅ **CouponEventHandler 구현**
  - 쿠폰 발급/사용 시 캐시 처리
- [x] ✅ **BalanceEventHandler 구현**
  - 잔액 변경 시 캐시 처리

### 2.3 RedisEventRouter 완성
- [x] ✅ **라우팅 로직 구현 완료**
  - 모든 Handler에게 적절히 라우팅
  - 기존 processBusinessLogic 제거

---

## 🚧 **Phase 3: Service 레이어 리팩토링 (진행 필요)**

### 3.1 OrderService.payOrder() 분리
- [ ] **현재 문제점**
  - 한 트랜잭션에서 5개 도메인 UseCase 호출
  - Service에서 Lock/Transaction 직접 관리
  
- [ ] **개선 방안**
  - 각 도메인이 이벤트로만 통신하도록 분리
  - OrderValidatedEvent → 잔액 차감
  - BalanceDeductedEvent → 재고 차감  
  - StockReservedEvent → 결제 생성
  - PaymentCreatedEvent → 주문 완료

### 3.2 Service 책임 분리
- [ ] **트랜잭션/락 관리를 UseCase로 이동**
  - Service: UseCase 호출 + 이벤트 발행만
  - UseCase: 트랜잭션/락 + 비즈니스 로직

### 3.3 누락된 이벤트 발행 추가
- [ ] **BalanceService에 이벤트 발행 추가**
  - chargeBalance() → BALANCE_CHARGED 이벤트
- [ ] **CouponService에 이벤트 발행 추가**  
  - issueCoupon() → COUPON_ISSUED 이벤트
- [ ] **PaymentService 생성**
  - 결제 도메인 독립
  - PAYMENT_COMPLETED 이벤트 발행

---

## 🧪 **Phase 3: 통합 테스트 및 검증**

### 3.1 도메인별 이벤트 처리 테스트
- [ ] **PaymentEventIntegrationTest**
  - 결제 완료 → 잔액 차감 → 재고 차감 → 주문 완료 전체 플로우
  - 각 단계별 독립적 처리 검증

- [ ] **BalanceEventIntegrationTest**
  - 잔액 변경 이벤트 처리 검증
  - 보상 트랜잭션 시나리오 테스트

- [ ] **ProductEventIntegrationTest**
  - 재고 변경 이벤트 처리 검증
  - 재고 부족 시 연쇄 이벤트 발생 검증

- [ ] **OrderEventIntegrationTest**
  - 주문 상태 변경 이벤트 처리 검증
  - 주문 취소 시나리오 테스트

### 3.2 End-to-End 플로우 테스트
- [ ] **PaymentToOrderCompletionE2ETest**
  - 결제 시작부터 주문 완료까지 전체 이벤트 체인 검증
  - 각 도메인의 독립적 처리 및 최종 일관성 확인

- [ ] **FailureScenarioE2ETest**
  - 중간 단계 실패 시 보상 트랜잭션 동작 검증
  - 이벤트 처리 실패 시 재시도 메커니즘 검증

### 3.3 ApplicationEventPublisher 완전 제거 검증
- [ ] **ApplicationEventPublisherRemovalTest**
  - 모든 도메인에서 ApplicationEventPublisher 사용 흔적 제거 확인
  - MessagingPort만 사용하는지 정적 분석

- [ ] **SingleEntryPointValidationTest**
  - 모든 이벤트가 MessagingPort를 통해서만 발행되는지 검증

---

## 🔧 **Phase 4: 성능 최적화 및 모니터링**

### 4.1 성능 최적화
- [ ] **MessagingPort 성능 튜닝**
  - 배치 이벤트 처리 최적화
  - Redis Streams 파티셔닝 전략 적용

- [ ] **이벤트 처리 병렬화**
  - 도메인별 EventHandler 병렬 실행
  - 독립적 도메인 간 처리 순서 최적화

### 4.2 모니터링 및 관찰성
- [ ] **MessagingPort 모니터링**
  - 이벤트 발행/처리 메트릭 수집
  - 도메인별 처리 시간 추적

- [ ] **이벤트 플로우 추적**
  - Correlation ID 기반 전체 플로우 추적
  - 각 도메인별 처리 상태 시각화

---

## 🚀 **Phase 5: Saga Pattern 적용 (차후 단계)**

### 5.1 Saga Orchestrator 구현
- [ ] **SagaState 엔티티 구현**
- [ ] **MessagingPort 기반 Saga Orchestrator**
- [ ] **보상 트랜잭션 프레임워크**

### 5.2 복잡한 비즈니스 플로우 Saga 적용  
- [ ] **OrderPaymentSaga**
  - 주문 생성 → 재고 예약 → 결제 → 배송 준비 전체 플로우
- [ ] **CouponIssuanceSaga**
  - 쿠폰 발급 → 사용자 알림 → 통계 업데이트 플로우

---

## 📊 **성공 지표 (KPI)**

### 기술적 지표
- [ ] **ApplicationEventPublisher 사용률: 100% → 0%**
- [ ] **MessagingPort 단일 진입점 비율: 100%**  
- [ ] **도메인 간 직접 호출 제거: 100%**
- [ ] **이벤트 처리 성능: 기존 대비 향상 측정**

### 비즈니스 지표
- [ ] **도메인 독립성: 각 도메인 장애 격리 확인**
- [ ] **확장성: 새 도메인 추가 시 기존 코드 수정 없음**
- [ ] **일관성: 최종 일관성 보장 확인**

---

## 🏁 **완료 기준**

1. ✅ **모든 UseCase에서 ApplicationEventPublisher 완전 제거**
2. ✅ **MessagingPort만을 통한 이벤트 발행/처리**  
3. ✅ **도메인별 EventHandler 독립적 비즈니스 로직 처리**
4. ✅ **전체 E2E 테스트 성공 (결제 → 잔액차감 → 재고차감 → 주문완료)**
5. ✅ **성능 지표 개선 확인**

---

> **Next Step**: Phase 2.1 PaymentEventHandler 구현부터 시작!
> 
> **핵심**: 각 도메인이 이벤트를 통해 **독립적으로 비즈니스 로직을 처리**하도록 하는 것이 목표