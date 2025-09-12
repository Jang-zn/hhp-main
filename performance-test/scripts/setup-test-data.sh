#!/bin/bash

# K6 부하테스트용 데이터 준비 스크립트
# 모든 테스트 시나리오에 필요한 데이터를 생성합니다

BASE_URL="http://localhost:8080"
HEADERS="Content-Type: application/json"
LOG_FILE="performance-test/logs/setup-$(date +%Y%m%d_%H%M%S).log"

# 로그 디렉토리 생성
mkdir -p performance-test/logs

# 로깅 함수
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" | tee -a "$LOG_FILE"
}

error() {
    echo "[ERROR] $1" | tee -a "$LOG_FILE" >&2
}

# API 응답 검증 함수
check_response() {
    local response="$1"
    local operation="$2"
    
    if [[ "$response" == *'"code":"S001"'* ]]; then
        return 0
    else
        error "$operation 실패: $response"
        return 1
    fi
}

# 서버 상태 확인
check_server() {
    log "서버 연결 상태 확인 중..."
    
    for i in {1..10}; do
        if curl -s --connect-timeout 5 "${BASE_URL}/actuator/health" > /dev/null; then
            log "서버 연결 성공"
            return 0
        fi
        
        log "서버 연결 시도 ${i}/10 실패, 5초 후 재시도..."
        sleep 5
    done
    
    error "서버에 연결할 수 없습니다. 서버가 실행 중인지 확인하세요."
    exit 1
}

# 기존 테스트 데이터 정리
cleanup_existing_data() {
    log "기존 테스트 데이터 정리 시작..."
    
    # MySQL에서 직접 삭제 (더 안전하고 빠름)
    mysql -h localhost -u root -proot hhplus -e "
        DELETE FROM coupon_history WHERE coupon_id IN (1, 2, 3);
        DELETE FROM coupon WHERE id IN (1, 2, 3);
        DELETE FROM order_item;
        DELETE FROM orders;
        DELETE FROM balance;
        DELETE FROM product WHERE id BETWEEN 1 AND 20;
        ALTER TABLE product AUTO_INCREMENT = 1;
        ALTER TABLE orders AUTO_INCREMENT = 1;
        ALTER TABLE coupon AUTO_INCREMENT = 1;
    " 2>/dev/null || log "데이터 정리 중 일부 실패 (정상적인 경우일 수 있음)"
    
    log "데이터 정리 완료"
}

log "=== K6 부하테스트 데이터 준비 시작 ==="
check_server
cleanup_existing_data

# 1. 상품 생성 (20개) - 상품 조회 테스트용
log "상품 데이터 생성 중... (20개)"
for i in {1..20}; do
    # 다양한 가격대와 충분한 재고
    price=$((RANDOM % 40000 + 20000))  # 20,000 ~ 60,000
    stock=$((RANDOM % 100 + 100))      # 100 ~ 200 (동시성 테스트 고려)
    
    response=$(curl -s -X POST "${BASE_URL}/api/product" \
        -H "$HEADERS" \
        -d "{\"name\": \"부하테스트 상품 ${i}\", \"price\": ${price}, \"stock\": ${stock}}")
    
    if ! check_response "$response" "상품 ${i} 생성"; then
        error "상품 생성 중단"
        exit 1
    fi
    
    # 진행률 표시
    if [ $((i % 5)) -eq 0 ]; then
        log "상품 ${i}/20개 생성 완료"
    fi
done

# 2. 사용자 잔액 설정 (1000명) - 쿠폰 스파이크 테스트용
log "사용자 잔액 설정 중... (1000명)"
for userId in {1..1000}; do
    amount=200000  # 20만원으로 고정 (주문 테스트에 충분한 금액)
    
    response=$(curl -s -X POST "${BASE_URL}/api/balance/charge" \
        -H "$HEADERS" \
        -d "{\"userId\": ${userId}, \"amount\": ${amount}}")
    
    if ! check_response "$response" "사용자 ${userId} 잔액 충전"; then
        # 잔액 충전 실패는 이미 존재하는 경우일 수 있으므로 계속 진행
        log "사용자 ${userId} 잔액 충전 실패 (이미 존재할 수 있음)"
    fi
    
    # 진행률 표시 (100명 단위)
    if [ $((userId % 100)) -eq 0 ]; then
        log "사용자 잔액 ${userId}/1000명 설정 완료"
    fi
done

# 3. 쿠폰 생성 (3개) - 쿠폰 테스트용
log "쿠폰 데이터 생성 중... (3개)"

# MySQL에 직접 쿠폰 삽입 (API가 없는 경우 대비)
mysql -h localhost -u root -proot hhplus -e "
    INSERT INTO coupon (id, name, discount_amount, total_quantity, issued_quantity, start_date, end_date, created_at, updated_at) VALUES 
    (1, '스파이크 테스트 쿠폰', 5000, 100, 0, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY), NOW(), NOW()),
    (2, '종합 테스트 쿠폰 A', 3000, 50, 0, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY), NOW(), NOW()),
    (3, '종합 테스트 쿠폰 B', 10000, 30, 0, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY), NOW(), NOW());
" 2>/dev/null

if [ $? -eq 0 ]; then
    log "쿠폰 3개 생성 완료"
else
    error "쿠폰 생성 실패"
    exit 1
fi

# 4. 데이터 생성 검증
log "=== 생성된 데이터 검증 중... ==="

# 상품 개수 확인
product_response=$(curl -s "${BASE_URL}/api/product/list?limit=25&offset=0")
if check_response "$product_response" "상품 조회"; then
    # 상품 데이터가 있는지 간단히 확인
    if [[ "$product_response" == *'"data":'* ]]; then
        log "✓ 상품: 데이터 존재 확인"
    else
        error "상품 데이터가 없습니다"
        exit 1
    fi
else
    error "상품 데이터 검증 실패"
    exit 1
fi

# 사용자 잔액 샘플 확인 (처음 3명만)
user_check_count=0
for userId in {1..3}; do
    balance_response=$(curl -s "${BASE_URL}/api/balance/${userId}")
    if check_response "$balance_response" "사용자 ${userId} 잔액 조회"; then
        # 잔액 데이터가 있는지 간단히 확인
        if [[ "$balance_response" == *'"amount":'* ]]; then
            ((user_check_count++))
        fi
    fi
done

log "✓ 사용자 잔액: ${user_check_count}/3명 확인"

# 쿠폰 개수 확인
coupon_count=$(mysql -h localhost -u root -proot hhplus -sN -e "SELECT COUNT(*) FROM coupon WHERE id IN (1,2,3);" 2>/dev/null)

if [ "$coupon_count" = "3" ]; then
    log "✓ 쿠폰: 3개 확인"
else
    error "쿠폰 데이터 검증 실패. 예상: 3개, 실제: ${coupon_count}개"
    exit 1
fi

log "=== 테스트 데이터 준비 완료 ==="
echo ""
echo "📊 생성된 데이터 요약:"
echo "   • 상품: 20개 (ID 1-20, 재고 100-200개)"
echo "   • 사용자: 1,000명 (ID 1-1000, 잔액 200,000원)"
echo "   • 쿠폰: 3개 (ID 1-3, 한정 수량 30-100개)"
echo ""
echo "🚀 K6 테스트 실행 준비 완료!"
echo "   다음 명령어로 테스트를 실행할 수 있습니다:"
echo "   • 상품 조회: k6 run performance-test/k6-scripts/01-product-load-test.js"
echo "   • 주문 동시성: k6 run performance-test/k6-scripts/02-order-concurrency-test.js"
echo "   • 쿠폰 스파이크: k6 run performance-test/k6-scripts/03-coupon-spike-test.js"
echo "   • 종합 부하: k6 run performance-test/k6-scripts/04-comprehensive-load-test.js"
echo ""
echo "📝 로그 파일: $LOG_FILE"