"""GMS(AI Portal) 공통 호출 모듈 — OpenAI 호환 엔드포인트.

채팅/임베딩 모두 base_url 만 GMS 로 바꿔 사용한다.
키는 환경변수 GMS_KEY 로 주입 (없으면 개발용 fallback).
"""
import os
import httpx

GMS_KEY = os.environ.get("GMS_KEY", "")
GMS_BASE = "https://gms.ssafy.io/gmsapi/api.openai.com/v1"
HEADERS = {"Authorization": f"Bearer {GMS_KEY}", "Content-Type": "application/json"}

CHAT_MODEL = "gpt-4.1-mini"
EMBED_MODEL = "text-embedding-3-small"
EMBED_DIM = 1536


def chat(messages, temperature=0.7, max_tokens=2000):
    """단발 채팅 호출 → 응답 텍스트."""
    with httpx.Client(timeout=120) as c:
        r = c.post(
            f"{GMS_BASE}/chat/completions",
            headers=HEADERS,
            json={
                "model": CHAT_MODEL,
                "messages": messages,
                "temperature": temperature,
                "max_tokens": max_tokens,
            },
        )
        r.raise_for_status()
        return r.json()["choices"][0]["message"]["content"]


def embed(texts):
    """문자열 리스트 → 임베딩 벡터 리스트(1536차원)."""
    if isinstance(texts, str):
        texts = [texts]
    with httpx.Client(timeout=120) as c:
        r = c.post(
            f"{GMS_BASE}/embeddings",
            headers=HEADERS,
            json={"model": EMBED_MODEL, "input": texts},
        )
        r.raise_for_status()
        data = sorted(r.json()["data"], key=lambda d: d["index"])
        return [d["embedding"] for d in data]
