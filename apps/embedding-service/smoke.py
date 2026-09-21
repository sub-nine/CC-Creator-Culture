"""모델 준비 후 추론 한 번만 확인한다. 부하를 발생시키지 않는다."""

import json
import math
import sys
import time
import urllib.error
import urllib.request


def check(base_url: str) -> None:
    base_url = base_url.rstrip("/")
    deadline = time.monotonic() + 120
    while True:
        try:
            with urllib.request.urlopen(f"{base_url}/docs", timeout=5) as response:
                if response.status != 200:
                    raise ValueError("health check did not return 200")
            break
        except (urllib.error.URLError, TimeoutError):
            if time.monotonic() >= deadline:
                raise
            time.sleep(2)

    request = urllib.request.Request(
        f"{base_url}/embed",
        data=json.dumps({"text": "클라우드 임베딩 연결 확인"}).encode(),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=60) as response:
        if response.status != 200:
            raise ValueError("embedding did not return 200")
        vector = json.load(response).get("vector")
    if (
        not isinstance(vector, list)
        or len(vector) != 768
        or not all(type(value) in (int, float) and math.isfinite(value) for value in vector)
    ):
        raise ValueError("expected 768 finite numeric vector components")
    print("embedding smoke passed: /docs ready, one /embed request, 768 finite values")


if __name__ == "__main__":
    check(sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8000")
