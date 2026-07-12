"""가당 RAG 서버 (FastAPI).

공유 코스 후기 코퍼스를 GMS 임베딩으로 벡터화해 두고,
질의를 임베딩하여 코사인 유사도 Top-K 로 검색해 돌려준다.

다이어그램상 'AI Server 분리'(F1209) 역할: Spring 은 Tool 로 이 서버를 호출한다.

실행:  uvicorn main:app --port 8000
"""
import json
import os

import numpy as np
from fastapi import FastAPI
from pydantic import BaseModel

from gms import embed

CORPUS_PATH = "corpus.json"
EMBED_CACHE = "embeddings.npy"

app = FastAPI(title="가당 RAG 서버")

_docs: list[dict] = []
_matrix: np.ndarray | None = None  # (N, 1536) L2 정규화된 임베딩


def _normalize(v: np.ndarray) -> np.ndarray:
    n = np.linalg.norm(v, axis=-1, keepdims=True)
    return v / np.clip(n, 1e-9, None)


@app.on_event("startup")
def load_index():
    """코퍼스 로드 + 임베딩(캐시 있으면 재사용)."""
    global _docs, _matrix
    if not os.path.exists(CORPUS_PATH):
        print(f"[RAG] {CORPUS_PATH} 없음 — generate_corpus.py 먼저 실행 필요")
        return
    with open(CORPUS_PATH, encoding="utf-8") as f:
        _docs = json.load(f)

    if os.path.exists(EMBED_CACHE):
        cached = np.load(EMBED_CACHE)
        if cached.shape[0] == len(_docs):
            _matrix = cached
            print(f"[RAG] 임베딩 캐시 로드: {_matrix.shape}")
            return

    texts = [f"{d.get('title','')}\n{d.get('content','')}" for d in _docs]
    vecs = np.array(embed(texts), dtype=np.float32)
    _matrix = _normalize(vecs)
    np.save(EMBED_CACHE, _matrix)
    print(f"[RAG] 임베딩 생성·캐시: {_matrix.shape}")


class SearchRequest(BaseModel):
    query: str
    top_k: int = 3
    region: str | None = None  # 지역 사전 필터(선택)


class SearchHit(BaseModel):
    post_id: int
    region: str
    theme: str
    title: str
    content: str
    score: float


@app.get("/health")
def health():
    return {"status": "ok", "docs": len(_docs),
            "indexed": _matrix is not None}


@app.post("/search", response_model=list[SearchHit])
def search(req: SearchRequest):
    if _matrix is None or not _docs:
        return []

    # 지역 필터: 후보 인덱스 제한
    idxs = [i for i, d in enumerate(_docs)
            if not req.region or req.region in d.get("region", "")]
    if not idxs:
        idxs = list(range(len(_docs)))

    q = _normalize(np.array(embed(req.query)[0], dtype=np.float32))
    sims = _matrix[idxs] @ q
    order = np.argsort(-sims)[: req.top_k]

    hits = []
    for o in order:
        d = _docs[idxs[o]]
        hits.append(SearchHit(
            post_id=d.get("post_id", 0),
            region=d.get("region", ""),
            theme=d.get("theme", ""),
            title=d.get("title", ""),
            content=d.get("content", ""),
            score=float(sims[o]),
        ))
    return hits
