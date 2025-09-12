import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { SharedArray } from 'k6/data';
import { Rate, Counter, Trend } from 'k6/metrics';

const errorRate = new Rate('errors');
const apiDuration = new Trend('api_duration');

// 테스트 데이터
const userIds = new SharedArray('users', function () {
  return Array.from({ length: 100 }, (_, i) => i + 1);
});

const productIds = new SharedArray('products', function () {
  return [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];
});

export const options = {
  stages: [
    { duration: '2m', target: 50 },   // 워밍업
    { duration: '5m', target: 100 },  // 정상 부하
    { duration: '3m', target: 150 },  // 피크 부하
    { duration: '2m', target: 50 },   // 정상화
    { duration: '1m', target: 0 },    // 쿨다운
  ],
  thresholds: {
    http_req_duration: ['p(95)<1500'],  // 95%의 요청이 1.5초 이내
    errors: ['rate<0.1'],                // 에러율 10% 미만
  },
};

const BASE_URL = 'http://localhost:8080';

export default function () {
  const userId = userIds[Math.floor(Math.random() * userIds.length)];
  const scenario = Math.random();
  
  // 시나리오 분배: 조회(60%), 주문(25%), 쿠폰(15%)
  if (scenario < 0.6) {
    productBrowsingScenario(userId);
  } else if (scenario < 0.85) {
    orderScenario(userId);
  } else {
    couponScenario(userId);
  }
  
  sleep(Math.random() * 2 + 1); // 1-3초 대기
}

// 상품 조회 시나리오
function productBrowsingScenario(userId) {
  group('상품 조회', function () {
    // 상품 목록 조회
    const listRes = http.get(`${BASE_URL}/api/product/list?limit=20`);
    const listSuccess = check(listRes, {
      '상품 목록 조회 성공': (r) => r.status === 200,
    });
    
    if (listSuccess) {
      const products = listRes.json('data');
      if (products && products.length > 0) {
        // 상품 상세 조회 (2-3개)
        const viewCount = Math.min(Math.floor(Math.random() * 2) + 2, products.length);
        for (let i = 0; i < viewCount; i++) {
          const product = products[Math.floor(Math.random() * products.length)];
          const detailRes = http.get(`${BASE_URL}/api/product/${product.id}`);
          check(detailRes, {
            '상품 상세 조회 성공': (r) => r.status === 200,
          });
          sleep(0.5);
        }
      }
    }
    
    errorRate.add(!listSuccess);
  });
}

// 주문 시나리오
function orderScenario(userId) {
  group('주문 처리', function () {
    const headers = { 'Content-Type': 'application/json' };
    
    // 잔액 조회
    const balanceRes = http.get(`${BASE_URL}/api/balance/${userId}`);
    check(balanceRes, {
      '잔액 조회 성공': (r) => r.status === 200,
    });
    
    // 상품 선택
    const items = [];
    const itemCount = Math.floor(Math.random() * 2) + 1; // 1-2개 상품
    
    for (let i = 0; i < itemCount; i++) {
      items.push({
        productId: productIds[Math.floor(Math.random() * productIds.length)],
        quantity: Math.floor(Math.random() * 2) + 1,
      });
    }
    
    // 주문 생성
    const orderPayload = JSON.stringify({
      userId: userId,
      products: items,
    });
    
    const orderRes = http.post(`${BASE_URL}/api/order`, orderPayload, { headers });
    const orderSuccess = check(orderRes, {
      '주문 생성 성공': (r) => r.status === 201,
      '재고 부족': (r) => r.status === 409,
    });
    
    if (orderSuccess && orderRes.status === 201) {
      const order = orderRes.json('data');
      
      // 잔액 충전 (필요시)
      if (Math.random() < 0.4) {
        const chargePayload = JSON.stringify({
          userId: userId,
          amount: 50000,
        });
        
        const chargeRes = http.post(`${BASE_URL}/api/balance/charge`, chargePayload, { headers });
        check(chargeRes, {
          '잔액 충전 성공': (r) => r.status === 200,
        });
        sleep(0.5);
      }
      
      // 결제
      const paymentPayload = JSON.stringify({
        orderId: order.orderId,
        userId: userId,
      });
      
      const paymentRes = http.post(`${BASE_URL}/api/order/${order.orderId}/pay`, paymentPayload, { headers });
      check(paymentRes, {
        '결제 성공': (r) => r.status === 200,
        '잔액 부족': (r) => r.status === 402,
      });
    }
    
    errorRate.add(!orderSuccess);
  });
}

// 쿠폰 시나리오
function couponScenario(userId) {
  group('쿠폰 처리', function () {
    const headers = { 'Content-Type': 'application/json' };
    
    // 쿠폰 목록 조회 (일단 주석 처리 - API가 없을 수 있음)
    // const couponListRes = http.get(`${BASE_URL}/api/coupon/list`);
    // check(couponListRes, {
    //   '쿠폰 목록 조회 성공': (r) => r.status === 200,
    // });
    
    // 쿠폰 발급 시도
    const couponId = Math.floor(Math.random() * 3) + 1; // 1-3번 쿠폰
    const issueRes = http.post(
      `${BASE_URL}/api/coupon/issue`,
      JSON.stringify({ userId: userId, couponId: couponId }),
      { headers }
    );
    
    const issueSuccess = check(issueRes, {
      '쿠폰 발급 성공': (r) => r.status === 201,
      '이미 발급됨': (r) => r.status === 400,
      '쿠폰 소진': (r) => r.status === 410,
    });
    
    // 내 쿠폰 조회
    if (issueSuccess) {
      sleep(0.5);
      const myCouponsRes = http.get(`${BASE_URL}/api/coupon/user/${userId}`);
      check(myCouponsRes, {
        '내 쿠폰 조회 성곰': (r) => r.status === 200,
      });
    }
    
    errorRate.add(!issueSuccess);
  });
}

export function handleSummary(data) {
  return {
    'stdout': textSummary(data, { indent: ' ', enableColors: true }),
    'performance-test/results/comprehensive-load-test.json': JSON.stringify(data),
  };
}

function textSummary(data, options) {
  const { metrics } = data;
  
  return `
=== 종합 부하 테스트 결과 ===

✓ 전체 요청 통계:
  - 총 요청 수: ${metrics.http_reqs.values.count}
  - 성공률: ${((1 - metrics.errors.values.rate) * 100).toFixed(2)}%
  - 평균 응답 시간: ${metrics.http_req_duration.values.avg.toFixed(2)}ms
  - P95 응답 시간: ${metrics.http_req_duration.values['p(95)'].toFixed(2)}ms
  - P99 응답 시간: ${metrics.http_req_duration.values['p(99)'].toFixed(2)}ms
  
✓ 가상 사용자:
  - 최대 동시 사용자: 150명
  - 평균 처리량: ${(metrics.http_reqs.values.rate || 0).toFixed(2)} req/s
  
✓ 시나리오별 분포:
  - 상품 조회: ~60%
  - 주문 처리: ~25%  
  - 쿠폰 처리: ~15%
  
✓ 시스템 안정성:
  - 에러율: ${(metrics.errors.values.rate * 100).toFixed(2)}%
  - 목표 달성: ${metrics.errors.values.rate < 0.1 ? '✅ 통과' : '❌ 실패'}
`;
}