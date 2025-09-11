import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const errorRate = new Rate('errors');
const productListDuration = new Trend('product_list_duration');
const productDetailDuration = new Trend('product_detail_duration');

export const options = {
  stages: [
    { duration: '1m', target: 20 },   // 워밍업
    { duration: '3m', target: 50 },   // 정상 부하
    { duration: '2m', target: 100 },  // 높은 부하
    { duration: '1m', target: 0 },    // 쿨다운
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'], // 95%의 요청이 500ms 이내
    errors: ['rate<0.1'],              // 에러율 10% 미만
  },
};

const BASE_URL = 'http://localhost:8080';

export default function () {
  // 상품 목록 조회 (70%)
  if (Math.random() < 0.7) {
    const offset = Math.floor(Math.random() * 100);
    const startTime = Date.now();
    const listRes = http.get(`${BASE_URL}/api/product/list?limit=20&offset=${offset}`);
    productListDuration.add(Date.now() - startTime);
    
    const success = check(listRes, {
      '상품 목록 조회 성공': (r) => r.status === 200,
      '응답 시간 < 300ms': (r) => r.timings.duration < 300,
    });
    errorRate.add(!success);
    
    // 목록에서 상품 하나 선택하여 상세 조회
    if (success && listRes.json('data') && listRes.json('data').length > 0) {
      const products = listRes.json('data');
      const randomProduct = products[Math.floor(Math.random() * products.length)];
      
      const detailStartTime = Date.now();
      const detailRes = http.get(`${BASE_URL}/api/product/${randomProduct.id}`);
      productDetailDuration.add(Date.now() - detailStartTime);
      
      check(detailRes, {
        '상품 상세 조회 성공': (r) => r.status === 200,
        '응답 시간 < 200ms': (r) => r.timings.duration < 200,
      });
    }
  } 
  // 인기 상품 조회 (30%)
  else {
    const popularRes = http.get(`${BASE_URL}/api/product/popular`);
    check(popularRes, {
      '인기 상품 조회 성공': (r) => r.status === 200,
      '캐시 히트': (r) => r.timings.duration < 100, // 캐시된 경우 100ms 이내
    });
  }
  
  sleep(Math.random() * 2 + 1); // 1-3초 대기
}

export function handleSummary(data) {
  return {
    'stdout': textSummary(data, { indent: ' ', enableColors: true }),
    'performance-test/results/product-load-test.json': JSON.stringify(data),
  };
}

function textSummary(data, options) {
  const { metrics } = data;
  
  return `
=== 상품 조회 부하 테스트 결과 ===

✓ 요청 통계:
  - 총 요청 수: ${metrics.http_reqs.values.count}
  - 성공률: ${((1 - metrics.errors.values.rate) * 100).toFixed(2)}%
  - 평균 응답 시간: ${metrics.http_req_duration.values.avg.toFixed(2)}ms
  - P95 응답 시간: ${metrics.http_req_duration.values['p(95)'].toFixed(2)}ms
  
✓ 상품 목록 조회:
  - 평균 응답 시간: ${metrics.product_list_duration.values.avg.toFixed(2)}ms
  
✓ 상품 상세 조회:
  - 평균 응답 시간: ${metrics.product_detail_duration.values.avg.toFixed(2)}ms
`;
}