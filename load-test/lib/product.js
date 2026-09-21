import http from 'k6/http';
import { sleep } from 'k6';
import { authHeaders } from './auth.js';

function postProduct(baseUrl, creatorToken, unique, productName, hashTags, tags) {
  const productRes = http.post(
    `${baseUrl}/api/v1/products`,
    {
      request: http.file(
        JSON.stringify({
          hashTags,
          name: `${productName} ${unique}`,
          content: 'k6 부하 테스트용으로 생성된 상품입니다.',
          skus: [{ name: '기본', price: 10000, isDefault: true, quantity: 1000000 }],
        }),
        'request.json',
        'application/json',
      ),
    },
    { headers: { Authorization: `Bearer ${creatorToken}` }, tags },
  );
  if (productRes.status !== 201) {
    throw new Error(`setup 실패 - 상품 등록 status=${productRes.status} body=${productRes.body}`);
  }
  return productRes.json().data.productId;
}

// creator 토큰으로 상품+SKU를 만들고 첫 SKU의 skuId를 반환한다
export function createProductWithSku(baseUrl, creatorToken, unique, productName, hashTags = ['k6test']) {
  const productId = postProduct(baseUrl, creatorToken, unique, productName, hashTags, {});

  const detailRes = http.get(`${baseUrl}/api/v1/products/${productId}`, authHeaders(creatorToken));
  if (detailRes.status !== 200) {
    throw new Error(`setup 실패 - 상품 조회 status=${detailRes.status} body=${detailRes.body}`);
  }
  return detailRes.json().data.skus[0].skuId;
}

// creator 토큰으로 상품만 등록하고 productId를 반환한다 (SKU 조회가 필요 없는 경우)
export function registerProduct(baseUrl, creatorToken, unique, productName, hashTags = ['k6test'], tags = {}) {
  return postProduct(baseUrl, creatorToken, unique, productName, hashTags, tags);
}

// 방금 등록한 상품의 hashtagId를 상품 상세 응답에서 가져온다. POST 응답(201) 직후 바로
// GET을 치면 해시태그 연결이 아직 안 끝나있는 경우가 있어(실제로 실패 재현됨) 짧게 재시도한다
export function waitForHashtagId(baseUrl, token, productId, maxAttempts = 5) {
  for (let attempt = 0; attempt < maxAttempts; attempt++) {
    const productRes = http.get(`${baseUrl}/api/v1/products/${productId}`, authHeaders(token));
    if (productRes.status !== 200) {
      throw new Error(`setup 실패 - 상품 조회 status=${productRes.status} body=${productRes.body}`);
    }
    const hashtags = productRes.json('data.hashtags');
    if (hashtags && hashtags.length > 0) {
      return hashtags[0].hashtagId;
    }
    sleep(0.3);
  }
  throw new Error(`setup 실패 - 상품(${productId})에서 hashtagId를 ${maxAttempts}회 재시도 후에도 못 찾음`);
}
