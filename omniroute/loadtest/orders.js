import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

export const options = {
  vus: 50,
  duration: '60s',
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<2000'],
  },
};

const ordersTotal = new Counter('orders_total');
const ordersNormal = new Counter('orders_normal');
const ordersForcedFail = new Counter('orders_forced_fail');
const orderDuration = new Trend('order_submission_duration');

const SKUS = [
  'SKU-001', 'SKU-002', 'SKU-003', 'SKU-004', 'SKU-005',
  'SKU-006', 'SKU-007', 'SKU-008', 'SKU-009', 'SKU-010'
];

const TARGET_URL = __ENV.TARGET_URL || 'http://omniroute-gateway:8080';
const RUN_ID = __ENV.TEST_RUN_ID || 'RUN';

export default function () {
  const isForcedFail = Math.random() < 0.15;
  const sku = SKUS[Math.floor(Math.random() * SKUS.length)];
  const qty = Math.floor(Math.random() * 3) + 1; // 1 to 3 items
  
  let totalAmount;
  if (isForcedFail) {
    totalAmount = 13.13;
  } else {
    // Normal order: $20.00 to $180.00
    totalAmount = Math.round((Math.random() * 160 + 20) * 100) / 100;
    if (totalAmount === 13.13) {
      totalAmount = 14.00;
    }
  }

  const payload = JSON.stringify({
    customerId: `CUST-k6-${RUN_ID}-${__VU}-${__ITER}`,
    items: [
      {
        sku: sku,
        quantity: qty,
      },
    ],
    totalAmount: totalAmount,
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
    timeout: '10s',
  };

  const start = Date.now();
  const res = http.post(`${TARGET_URL}/api/orders`, payload, params);
  const duration = Date.now() - start;

  orderDuration.add(duration);
  ordersTotal.add(1);

  if (isForcedFail) {
    ordersForcedFail.add(1);
  } else {
    ordersNormal.add(1);
  }

  check(res, {
    'status is 202': (r) => r.status === 202,
    'has orderId': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.orderId !== undefined && body.orderId !== null;
      } catch (e) {
        return false;
      }
    },
  });

  // Pacing: 0.6s to 1.0s sleep per VU
  sleep(0.6 + Math.random() * 0.4);
}
