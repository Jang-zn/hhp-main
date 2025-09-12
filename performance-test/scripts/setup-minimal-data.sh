#!/bin/bash

# K6 테스트용 최소 데이터 준비 스크립트
# API를 통해 필요한 최소 데이터만 생성합니다

BASE_URL="http://localhost:8080"
HEADERS="Content-Type: application/json"

# 로깅 함수
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

error() {
    echo "[ERROR] $1" >&2
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

log "=== 최소 테스트 데이터 준비 시작 ==="

# 서버 상태 확인
log "서버 연결 확인 중..."
if ! curl -s --connect-timeout 5 "${BASE_URL}/actuator/health" > /dev/null; then
    error "서버에 연결할 수 없습니다. 서버가 실행 중인지 확인하세요."
    exit 1
fi
log "서버 연결 성공"

# 1. 상품 생성 (20개)
log "상품 생성 중... (20개)"
for i in {1..20}; do
    price=$((RANDOM % 40000 + 20000))  # 20,000 ~ 60,000
    stock=$((RANDOM % 100 + 100))      # 100 ~ 200
    
    response=$(curl -s -X POST "${BASE_URL}/api/product" \
        -H "$HEADERS" \
        -d "{\"name\": \"부하테스트 상품 ${i}\", \"price\": ${price}, \"stock\": ${stock}}")
    
    if ! check_response "$response" "상품 ${i} 생성"; then
        error "상품 생성 실패"
        exit 1
    fi
    
    if [ $((i % 5)) -eq 0 ]; then
        log "상품 ${i}/20개 생성 완료"
    fi
done

# 2. 사용자 잔액 생성 (100명만 - 테스트 범위 축소)
log "사용자 잔액 생성 중... (100명)"
success_count=0
for userId in {1..100}; do
    response=$(curl -s -X POST "${BASE_URL}/api/balance/charge" \
        -H "$HEADERS" \
        -d "{\"userId\": ${userId}, \"amount\": 200000}")
    
    if check_response "$response" "사용자 ${userId} 잔액 충전"; then
        ((success_count++))
    fi
    
    if [ $((userId % 20)) -eq 0 ]; then
        log "사용자 ${userId}/100명 잔액 생성 시도 완료 (성공: ${success_count}명)"
    fi
done

# 3. MySQL에 쿠폰 직접 생성 (포트 명시)
log "쿠폰 생성 중... (3개)"
mysql -h 127.0.0.1 -P 3306 -u root -proot hhplus -e "
    INSERT IGNORE INTO coupon (id, name, discount_amount, total_quantity, issued_quantity, start_date, end_date, created_at, updated_at) VALUES 
    (1, '스파이크 테스트 쿠폰', 5000, 100, 0, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY), NOW(), NOW()),
    (2, '종합 테스트 쿠폰 A', 3000, 50, 0, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY), NOW(), NOW()),
    (3, '종합 테스트 쿠폰 B', 10000, 30, 0, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY), NOW(), NOW());
" 2>/dev/null

if [ $? -eq 0 ]; then
    log "쿠폰 3개 생성 완료"
else
    log "쿠폰 생성 실패 - 수동으로 생성 필요할 수 있음"
fi

# 4. 데이터 검증
log "=== 데이터 검증 중... ==="

# 상품 확인
product_response=$(curl -s "${BASE_URL}/api/product/list?limit=25&offset=0")
if check_response "$product_response" "상품 조회"; then
    if [[ "$product_response" == *'"data":'* ]]; then
        log "✓ 상품: 데이터 존재 확인"
    fi
fi

# 잔액 확인 (처음 3명)
balance_check=0
for userId in {1..3}; do
    balance_response=$(curl -s "${BASE_URL}/api/balance/${userId}")
    if check_response "$balance_response" "사용자 ${userId} 잔액 조회"; then
        ((balance_check++))
    fi
done

log "✓ 사용자 잔액: ${balance_check}/3명 확인"

# 쿠폰 확인
coupon_count=$(mysql -h 127.0.0.1 -P 3306 -u root -proot hhplus -sN -e "SELECT COUNT(*) FROM coupon WHERE id IN (1,2,3);" 2>/dev/null)
if [ "$coupon_count" = "3" ]; then
    log "✓ 쿠폰: 3개 확인"
else
    log "⚠ 쿠폰: 생성 확인 실패 (수동 확인 필요)"
fi

log "=== 최소 테스트 데이터 준비 완료 ==="
echo ""
echo "📊 생성된 데이터 요약:"
echo "   • 상품: 20개 (ID 1-20)"
echo "   • 사용자 잔액: ${success_count}명 (잔액 200,000원)"
echo "   • 쿠폰: 3개 (ID 1-3)"
echo ""
echo "🚀 K6 테스트 실행 준비 완료!"
echo "   k6 run performance-test/k6-scripts/01-product-load-test.js"