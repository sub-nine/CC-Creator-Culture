FROM python:3.12-slim@sha256:78387bc3881b8273120a12ebe6c1ab22b018ccc2c9adf565ae1ac9b536e184ea

WORKDIR /app

COPY requirements.txt .
RUN pip install torch --index-url https://download.pytorch.org/whl/cpu \
    && pip install -r requirements.txt

# 모델을 빌드 타임에 미리 받아서 이미지에 포함 - 기동 시 HuggingFace Hub 가용성에 의존하지 않게 함
RUN python -c "from sentence_transformers import SentenceTransformer; SentenceTransformer('jhgan/ko-sroberta-multitask')"

# 이미지에 받은 모델만 사용해 기동 중 Hub 메타데이터 재시도를 방지한다.
ENV HF_HUB_OFFLINE=1

COPY app.py smoke.py ./

EXPOSE 8000

CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]
