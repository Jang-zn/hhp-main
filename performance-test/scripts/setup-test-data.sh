#!/bin/bash

BASE_URL="http://localhost:8080"

echo "=== 테스트 데이터 생성 시작 ==="

# 상품 생성 (20개)
echo "상품 생성 중..."
for i in {1..20}; do
  price=$((RANDOM % 50000 + 10000))  # 10,000 ~ 60,000
  stock=$((RANDOM % 200 + 50))       # 50 ~ 250
  
  curl -X POST "${BASE_URL}/api/product" \
    -H "Content-Type: application/json" \
    -d "{\"name\": \"테스트 상품 ${i}\", \"price\": ${price}, \"stock\": ${stock}}" \
    -s > /dev/null
  
  if [ $((i % 5)) -eq 0 ]; then
    echo "상품 ${i}개 생성 완료"
  fi
done

# 사용자 잔액 충전 (10명)
echo "사용자 잔액 충전 중..."
for userId in {1..10}; do
  amount=$((RANDOM % 50000 + 100000))  # 100,000 ~ 150,000
  
  curl -X POST "${BASE_URL}/api/balance/charge" \
    -H "Content-Type: application/json" \
    -d "{\"userId\": ${userId}, \"amount\": ${amount}}" \
    -s > /dev/null
done

echo "=== 테스트 데이터 생성 완료 ==="

# 생성된 데이터 확인
echo ""
echo "=== 생성된 데이터 확인 ==="
echo "상품 목록 (처음 5개):"
curl -s "${BASE_URL}/api/product/list?limit=5&offset=0" | jq '.data'

echo ""
echo "사용자 1 잔액:"
curl -s "${BASE_URL}/api/balance/1" | jq '.data'