import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import { Rate, Counter } from 'k6/metrics';

const errorRate = new Rate('errors');
const stockErrors = new Counter('stock_errors');
const orderSuccess = new Counter('order_success');

// 테스트용 상품 ID 목록
const productIds = new SharedArray('products', function () {
  return [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];
});

// 테스트용 사용자 ID 목록
const userIds = new SharedArray('users', function () {
  return Array.from({ length: 100 }, (_, i) => i + 1);
});

export const options = {
  scenarios: {
    // 동시 주문 테스트 - 같은 상품에 대한 동시 주문
    concurrent_orders: {
      executor: 'constant-vus',
      vus: 50,
      duration: '2m',
      exec: 'concurrentOrders',
    },
    // 일반 주문 플로우 테스트
    normal_flow: {
      executor: 'ramping-vus',
      startVUs: 5,
      stages: [
        { duration: '1m', target: 20 },
        { duration: '2m', target: 30 },
        { duration: '1m', target: 10 },
      ],
      exec: 'normalOrderFlow',
    },
  },
  thresholds: {
    errors: ['rate<0.2'],                    // 에러율 20% 미만
    http_req_duration: ['p(95)<1000'],       // 95%의 요청이 1초 이내
    stock_errors: ['count<10'],              // 재고 에러 10건 미만
  },
};

const BASE_URL = 'http://localhost:8080';

// 동시 주문 시나리오 - 재고 동시성 테스트
export function concurrentOrders() {
  const userId = userIds[Math.floor(Math.random() * userIds.length)];
  const productId = productIds[0]; // 모든 VU가 같은 상품 주문 (동시성 테스트)
  
  // 주문 생성
  const orderPayload = JSON.stringify({
    userId: userId,
    items: [
      {
        productId: productId,
        quantity: 1,
      },
    ],
  });
  
  const headers = { 'Content-Type': 'application/json' };
  const orderRes = http.post(`${BASE_URL}/api/orders`, orderPayload, { headers });
  
  const orderCreated = check(orderRes, {
    '주문 생성 성공': (r) => r.status === 201,
    '재고 부족 에러': (r) => r.status === 409 && r.body.includes('OUT_OF_STOCK'),
  });
  
  if (orderRes.status === 409) {
    stockErrors.add(1);
  }
  
  if (orderCreated && orderRes.status === 201) {
    orderSuccess.add(1);
    const order = orderRes.json('data');
    
    // 결제 처리
    sleep(0.5); // 결제 준비 시간
    
    const paymentPayload = JSON.stringify({
      orderId: order.id,
      userId: userId,
    });
    
    const paymentRes = http.post(`${BASE_URL}/api/orders/${order.id}/pay`, paymentPayload, { headers });
    
    check(paymentRes, {
      '결제 성공': (r) => r.status === 200,
      '잔액 부족': (r) => r.status === 402,
    });
  }
  
  errorRate.add(!orderCreated);
  sleep(0.1); // 짧은 대기
}

// 일반 주문 플로우
export function normalOrderFlow() {
  const userId = userIds[Math.floor(Math.random() * userIds.length)];
  
  // 1. 상품 목록 조회
  const listRes = http.get(`${BASE_URL}/api/product/list?limit=10`);
  
  if (check(listRes, { '상품 조회 성공': (r) => r.status === 200 })) {
    const products = listRes.json('data');
    
    if (products && products.length > 0) {
      // 2. 1-3개 상품 선택
      const itemCount = Math.floor(Math.random() * 3) + 1;
      const selectedProducts = [];
      
      for (let i = 0; i < itemCount && i < products.length; i++) {
        selectedProducts.push({
          productId: products[i].id,
          quantity: Math.floor(Math.random() * 3) + 1,
        });
      }
      
      // 3. 주문 생성
      const orderPayload = JSON.stringify({
        userId: userId,
        items: selectedProducts,
      });
      
      const headers = { 'Content-Type': 'application/json' };
      const orderRes = http.post(`${BASE_URL}/api/orders`, orderPayload, { headers });
      
      if (check(orderRes, { '주문 생성 성공': (r) => r.status === 201 })) {
        const order = orderRes.json('data');
        
        // 4. 잔액 충전 (필요시)
        if (Math.random() < 0.3) { // 30% 확률로 충전
          const chargePayload = JSON.stringify({
            userId: userId,
            amount: order.totalAmount + 10000,
          });
          
          http.post(`${BASE_URL}/api/balance/charge`, chargePayload, { headers });
          sleep(0.5);
        }
        
        // 5. 결제 시도
        const paymentPayload = JSON.stringify({
          orderId: order.id,
          userId: userId,
        });
        
        const paymentRes = http.post(`${BASE_URL}/api/orders/${order.id}/pay`, paymentPayload, { headers });
        
        check(paymentRes, {
          '결제 완료': (r) => r.status === 200,
        });
      }
    }
  }
  
  sleep(Math.random() * 3 + 2); // 2-5초 대기
}

export function handleSummary(data) {
  return {
    'stdout': textSummary(data, { indent: ' ', enableColors: true }),
    'performance-test/results/order-concurrency-test.json': JSON.stringify(data),
  };
}

function textSummary(data, options) {
  const { metrics } = data;
  
  return `
=== 주문 동시성 테스트 결과 ===

✓ 요청 통계:
  - 총 요청 수: ${metrics.http_reqs.values.count}
  - 성공률: ${((1 - metrics.errors.values.rate) * 100).toFixed(2)}%
  - 평균 응답 시간: ${metrics.http_req_duration.values.avg.toFixed(2)}ms
  
✓ 주문 처리:
  - 성공한 주문: ${metrics.order_success.values.count}
  - 재고 부족 에러: ${metrics.stock_errors.values.count}
  
✓ 동시성 제어:
  - 재고 정합성 확인 필요
  - 데이터베이스 로그 분석 권장
`;
}