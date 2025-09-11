import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import { Rate, Counter, Trend } from 'k6/metrics';

const errorRate = new Rate('errors');
const couponIssued = new Counter('coupon_issued');
const couponFailed = new Counter('coupon_failed');
const issuanceTime = new Trend('coupon_issuance_time');

// 테스트용 사용자 ID
const userIds = new SharedArray('users', function () {
  return Array.from({ length: 1000 }, (_, i) => i + 1);
});

export const options = {
  scenarios: {
    // 스파이크 테스트 - 갑작스런 대량 요청
    spike_test: {
      executor: 'ramping-vus',
      startVUs: 10,
      stages: [
        { duration: '10s', target: 10 },   // 대기
        { duration: '5s', target: 300 },   // 급격한 증가
        { duration: '30s', target: 300 },  // 스파이크 유지
        { duration: '10s', target: 10 },   // 정상화
      ],
    },
  },
  thresholds: {
    errors: ['rate<0.5'],                     // 에러율 50% 미만 (경쟁 상황)
    http_req_duration: ['p(95)<2000'],        // 95%의 요청이 2초 이내
    coupon_issued: ['count>0', 'count<=100'], // 정확히 100개 이하 발급
  },
};

const BASE_URL = 'http://localhost:8080';
const COUPON_ID = 1; // 테스트용 쿠폰 ID (한정 수량 100개 가정)

export default function () {
  const userId = userIds[__VU - 1] || Math.floor(Math.random() * 1000) + 1;
  
  // 쿠폰 발급 요청
  const startTime = Date.now();
  const headers = { 'Content-Type': 'application/json' };
  
  const issueRes = http.post(
    `${BASE_URL}/api/coupons/${COUPON_ID}/issue`,
    JSON.stringify({ userId: userId }),
    { headers }
  );
  
  const duration = Date.now() - startTime;
  issuanceTime.add(duration);
  
  const success = check(issueRes, {
    '쿠폰 발급 성공': (r) => r.status === 201,
    '이미 발급됨': (r) => r.status === 400 && r.body.includes('ALREADY_ISSUED'),
    '재고 소진': (r) => r.status === 410 && r.body.includes('LIMIT_EXCEEDED'),
  });
  
  if (issueRes.status === 201) {
    couponIssued.add(1);
  } else {
    couponFailed.add(1);
  }
  
  errorRate.add(!success);
  
  // 발급 성공한 경우 쿠폰 조회
  if (issueRes.status === 201) {
    sleep(0.5);
    
    const myCouponsRes = http.get(`${BASE_URL}/api/coupons/user/${userId}`);
    check(myCouponsRes, {
      '내 쿠폰 조회 성공': (r) => r.status === 200,
      '발급된 쿠폰 확인': (r) => {
        if (r.status !== 200) return false;
        const coupons = r.json('data');
        return coupons && coupons.length > 0;
      },
    });
  }
  
  sleep(Math.random() * 0.1); // 0-100ms 대기
}

export function handleSummary(data) {
  const issuedCount = data.metrics.coupon_issued.values.count;
  const failedCount = data.metrics.coupon_failed.values.count;
  const totalAttempts = issuedCount + failedCount;
  
  return {
    'stdout': textSummary(data, { indent: ' ', enableColors: true }),
    'performance-test/results/coupon-spike-test.json': JSON.stringify(data),
  };
}

function textSummary(data, options) {
  const { metrics } = data;
  const issuedCount = metrics.coupon_issued.values.count;
  const failedCount = metrics.coupon_failed.values.count;
  const totalAttempts = issuedCount + failedCount;
  
  return `
=== 쿠폰 스파이크 테스트 결과 ===

✓ 발급 통계:
  - 총 시도: ${totalAttempts}
  - 발급 성공: ${issuedCount} (${((issuedCount / totalAttempts) * 100).toFixed(2)}%)
  - 발급 실패: ${failedCount} (${((failedCount / totalAttempts) * 100).toFixed(2)}%)
  
✓ 성능 지표:
  - 평균 발급 시간: ${metrics.coupon_issuance_time.values.avg.toFixed(2)}ms
  - P95 발급 시간: ${metrics.coupon_issuance_time.values['p(95)']?.toFixed(2) || 'N/A'}ms
  - 최대 발급 시간: ${metrics.coupon_issuance_time.values.max.toFixed(2)}ms
  
✓ 동시성 제어 검증:
  - 목표 발급 수량: 100개
  - 실제 발급 수량: ${issuedCount}개
  - 정합성: ${issuedCount <= 100 ? '✅ 통과' : '❌ 실패 (초과 발급)'}
`;
}