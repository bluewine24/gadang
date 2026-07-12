"""생성형 AI(GMS gpt-4.1-mini)로 당일치기 코스 후기 코퍼스를 생성한다.

RAG 검색 대상이 되는 '공유 코스 글' 예시 데이터를 LLM 으로 구성한다.
(관통PJT: 생성형 AI를 활용한 데이터 구성 — 본 프롬프트가 제출 산출물)

실행:  python generate_corpus.py   →  corpus.json 생성
"""
import json
import re
import sys

# Windows 콘솔(cp949)에서 한글·em-dash 출력 시 크래시 방지
try:
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
except Exception:  # noqa: BLE001
    pass

from gms import chat  # noqa: E402

# 코스 엔진이 지원하는 주요 당일치기 지역 + 인기 지역
REGIONS = [
    "서울", "부산", "경주", "강릉", "여수", "전주",
    "대전", "대구", "인천", "수원", "춘천", "속초",
    "공주", "안동", "통영", "군산",
]

THEMES = ["가성비 알뜰", "맛집 투어", "감성 카페·포토스팟", "문화·역사 탐방"]

SYSTEM = "너는 당일치기 여행 후기를 쓰는 한국인 여행 블로거다. 실제 장소명을 쓰되 과장 없이 현실적으로 쓴다."

PROMPT_TMPL = """'{region}' 당일치기 여행 코스 후기 글 1편을 작성해줘.
테마: {theme}

조건:
- 제목 1줄 + 본문 6~10문장.
- 본문에는 실제 있을 법한 장소 3~5곳을 방문 순서대로, 교통수단·대략 비용·소요시간을 자연스럽게 녹여서.
- 총 예상 비용과 한 줄 팁으로 마무리.
- 광고 문구 없이 담백하게.

아래 JSON 형식으로만 답해. 다른 말 금지.
{{"region":"{region}","theme":"{theme}","title":"...","content":"..."}}"""


def extract_json(text):
    """LLM 응답에서 JSON 오브젝트만 안전하게 추출."""
    text = text.strip()
    if text.startswith("```"):
        text = re.sub(r"^```(json)?", "", text).rsplit("```", 1)[0].strip()
    m = re.search(r"\{.*\}", text, re.DOTALL)
    return json.loads(m.group(0)) if m else None


def main():
    docs = []
    pid = 1
    for region in REGIONS:
        # 지역마다 테마 2개씩 (총 ~32편)
        for theme in THEMES[:2]:
            prompt = PROMPT_TMPL.format(region=region, theme=theme)
            try:
                raw = chat(
                    [{"role": "system", "content": SYSTEM},
                     {"role": "user", "content": prompt}],
                    temperature=0.8,
                )
                doc = extract_json(raw)
                if not doc or not doc.get("content"):
                    print(f"  ! skip {region}/{theme} (parse fail)", file=sys.stderr)
                    continue
                doc["post_id"] = pid
                docs.append(doc)
                pid += 1
                print(f"  + [{doc['post_id']}] {region} / {theme}: {doc['title'][:30]}")
            except Exception as e:  # noqa: BLE001
                print(f"  ! error {region}/{theme}: {e}", file=sys.stderr)

    with open("corpus.json", "w", encoding="utf-8") as f:
        json.dump(docs, f, ensure_ascii=False, indent=2)
    print(f"\n생성 완료: {len(docs)}편 → corpus.json")


if __name__ == "__main__":
    main()
