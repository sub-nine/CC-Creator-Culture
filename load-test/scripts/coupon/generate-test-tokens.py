#!/usr/bin/env python3
"""
k6 쿠폰 발급 테스트용 사용자 JWT 생성기

확정된 JWT 계약(HS256 / sub·role·jti·iat·exp·type)에 맞춰
Access Token을 N개 생성하고 CSV로 저장한다.

쿠폰 발급 경로는 p_users를 조회하지 않으므로(물리 FK 없음)
사용자 서비스에 실제 계정을 만들지 않아도 된다.

사용법
    export JWT_SECRET='<Base64 인코딩된 비밀키>'
    python3 generate-test-tokens.py

주의
    Access Token 만료가 1,800초(30분)다. 측정 직전에 생성할 것.
"""

import argparse
import base64
import csv
import hashlib
import hmac
import json
import os
from pathlib import Path
import sys
import time
import uuid

ACCESS_TOKEN_TTL = 1800  # 초. JWT 계약 확정값


def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def uuid7() -> str:
    """UUID v7 — p_users.id 형식에 맞춘 시간 정렬 UUID"""
    ms = int(time.time() * 1000)
    b = bytearray(ms.to_bytes(6, "big") + os.urandom(10))
    b[6] = (b[6] & 0x0F) | 0x70   # version 7
    b[8] = (b[8] & 0x3F) | 0x80   # variant 10
    return str(uuid.UUID(bytes=bytes(b)))


def make_access_token(secret: bytes, sub: str, role: str, ttl: int) -> str:
    now = int(time.time())
    header = {"alg": "HS256", "typ": "JWT"}
    payload = {
        "sub": sub,
        "role": role,
        "jti": str(uuid.uuid4()),
        "iat": now,
        "exp": now + ttl,
        "type": "ACCESS",
    }
    signing_input = "{}.{}".format(
        b64url(json.dumps(header, separators=(",", ":")).encode()),
        b64url(json.dumps(payload, separators=(",", ":")).encode()),
    )
    signature = hmac.new(secret, signing_input.encode(), hashlib.sha256).digest()
    return "{}.{}".format(signing_input, b64url(signature))


def main() -> int:
    parser = argparse.ArgumentParser()
    default_output = (
        Path(__file__).resolve().parents[2]
        / "data"
        / "coupon-user.csv"
    )
    parser.add_argument("--count", type=int, default=500, help="생성할 사용자 수")
    parser.add_argument("--out", default=str(default_output), help="출력 CSV 경로")
    parser.add_argument("--role", default="CUSTOMER")
    parser.add_argument("--ttl", type=int, default=ACCESS_TOKEN_TTL)
    args = parser.parse_args()

    raw = os.environ.get("JWT_SECRET")
    if not raw:
        print("JWT_SECRET 환경변수가 필요합니다.", file=sys.stderr)
        return 1

    try:
        secret = base64.b64decode(raw)
    except Exception:
        print("JWT_SECRET은 Base64 인코딩된 값이어야 합니다.", file=sys.stderr)
        return 1

    if len(secret) < 32:
        print("비밀키는 디코딩 후 32바이트 이상이어야 합니다. "
              "현재 {}바이트".format(len(secret)), file=sys.stderr)
        return 1

    output_path = Path(args.out)
    output_path.parent.mkdir(parents=True, exist_ok=True)

    # k6 SharedArray에서 읽는 CSV다. 각 사용자는 한 번만 사용한다.
    with output_path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["userId", "token"])
        for _ in range(args.count):
            user_id = uuid7()
            writer.writerow([user_id, make_access_token(secret, user_id, args.role, args.ttl)])

    expires_at = time.strftime("%H:%M:%S", time.localtime(time.time() + args.ttl))
    print("{}건 생성 완료 -> {}".format(args.count, output_path))
    print("만료 시각: {} (약 {}분 뒤)".format(expires_at, args.ttl // 60))
    return 0


if __name__ == "__main__":
    sys.exit(main())
