"""E2E CI의 첫 실패 단계를 Step Summary에 남긴다 (Python 3 표준 라이브러리만 사용).

정책 원본: docs/testing/fe06-e2e-ci.md Q10(Step Summary의 실패 단계).
- 입력은 env `STEP_OUTCOMES`: `<step id>=<outcome>`를 실행 순서대로 공백으로 이은 값이다. Workflow가 `steps.<id>.outcome`
  식으로 만든다(PR에서 온 값이 아니다).
- 형식(`[a-z][a-z0-9-]*=success|failure|cancelled|skipped`)에 맞는 토큰만 읽는다. 그 밖의 토큰은 버리고 출력하지 않는다.
- 첫 failure·cancelled 단계를 고정 이름으로 적는다. 찾지 못하면 "확인 불가"다(예: Job 시간 초과). 오류 원문은 다루지 않는다.
"""

import os
import re
import sys

TOKEN = re.compile(r"([a-z][a-z0-9-]*)=(success|failure|cancelled|skipped)")
FAILED = ("failure", "cancelled")


def first_failure(text):
    for token in (text or "").split():
        m = TOKEN.fullmatch(token)
        if m and m.group(2) in FAILED:
            return m.group(1), m.group(2)
    return None


def main(env=None):
    env = os.environ if env is None else env
    found = first_failure(env.get("STEP_OUTCOMES"))
    label = f"{found[0]} ({found[1]})" if found else "확인 불가"
    print(f"failed-step: {label}")
    summary = env.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as f:
            f.write(f"### E2E 실패 단계\n\n| 항목 | 값 |\n| --- | --- |\n| 실패 단계 | {label} |\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
