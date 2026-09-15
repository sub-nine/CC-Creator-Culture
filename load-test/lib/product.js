import http from 'k6/http';
import { authHeaders } from './auth.js';

// creator 토큰으로 상품+SKU를 만들고 첫 SKU의 skuId를 반환한다
export function createProductWithSku(baseUrl, creatorToken, unique, productName) {
  const productRes = http.post(
    `${baseUrl}/api/v1/products`,
    {
      request: http.file(
        JSON.stringify({
          hashTags: ['k6test'],
          name: `${productName} ${unique}`,
          content: 'k6 부하 테스트용으로 생성된 상품입니다.',
          skus: [{ name: '기본', price: 10000, isDefault: true, quantity: 1000000 }],
        }),
        'request.json',
        'application/json',
      ),
    },
    { headers: { Authorization: `Bearer ${creatorToken}` } },
  );
  if (productRes.status !== 201) {
    throw new Error(`setup 실패 - 상품 등록 status=${productRes.status} body=${productRes.body}`);
  }
  const productId = productRes.json().data.productId;

  const detailRes = http.get(`${baseUrl}/api/v1/products/${productId}`, authHeaders(creatorToken));
  if (detailRes.status !== 200) {
    throw new Error(`setup 실패 - 상품 조회 status=${detailRes.status} body=${detailRes.body}`);
  }
  return detailRes.json().data.skus[0].skuId;
}
