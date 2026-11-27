"""HIBP 스텁 호출 증거 확인 (Python 3 표준 라이브러리만 사용).

정책 원본: docs/testing/fe06-e2e-ci.md Q5.
- 통계 파일은 스텁(frontend/e2e/support/hibpStub.ts)이 기동할 때 0으로 초기화하고 요청마다 원자적으로 다시 쓴다.
  그래서 스텁이 아직 떠 있어도 최신 값을 읽을 수 있다.
- 최소 정상 호출 수(--min-ok)는 실행한 E2E 테스트 수다(e2e_report 출력). 테스트마다 새 계정을 하나 만들고
  가입마다 Backend가 HIBP range API를 한 번 부른다. Health Check는 정상 호출로 세지 않는다.
- 통계 누락·형식 오류, 정상 호출 부족, 예상 밖 요청이 1건이라도 있으면 실패다. 출력은 개수만 쓴다.
"""

import argparse
import json
import os
import sys
from dataclasses import dataclass, field

KEYS = ("ok", "unexpected", "health")


@dataclass
class Result:
    passed: bool
    counts: dict = field(default_factory=dict)
    problems: list = field(default_factory=list)


def _count(value):
    return isinstance(value, int) and not isinstance(value, bool) and value >= 0


def check(stats, min_ok):
    if not isinstance(min_ok, int) or isinstance(min_ok, bool) or min_ok < 1:
        return Result(False, problems=["최소 정상 호출 수가 1 이상이 아님"])
    if not isinstance(stats, dict) or not all(k in stats and _count(stats[k]) for k in KEYS):
        return Result(False, problems=["통계 누락 또는 형식 오류"])
    counts = {k: stats[k] for k in KEYS}
    problems = []
    if counts["ok"] < min_ok:
        problems.append("정상 호출 부족")
    if counts["unexpected"] > 0:
        problems.append("예상 밖 요청 있음")
    return Result(not problems, counts, problems)


def main(argv=None, env=None):
    env = os.environ if env is None else env
    p = argparse.ArgumentParser(add_help=False)
    p.add_argument("--stats-file", required=True)
    p.add_argument("--min-ok", required=True)
    try:
        args = p.parse_args(argv)
    except SystemExit:
        print("hibp-stub-check: 인자 오류")
        return 1
    try:
        min_ok = int(args.min_ok)
    except ValueError:
        min_ok = 0
    try:
        with open(args.stats_file, encoding="utf-8") as f:
            stats = json.load(f)
    except (OSError, ValueError, UnicodeDecodeError):
        stats = None
    r = check(stats, min_ok)
    c = r.counts
    print(f"hibp-stub-check: passed={r.passed} min_ok={min_ok} "
          + " ".join(f"{k}={c.get(k, '-')}" for k in KEYS))
    for msg in r.problems:
        print(f"hibp-stub-check: 문제 {msg}")
    summary = env.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as f:
            f.write("### HIBP 스텁 호출\n\n| 항목 | 값 |\n| --- | --- |\n")
            f.write(f"| 판정 | {'PASS' if r.passed else 'FAIL'} |\n| 최소 정상 호출 | {min_ok} |\n")
            f.write(f"| 정상 호출 | {c.get('ok', '-')} |\n| 예상 밖 요청 | {c.get('unexpected', '-')} |\n")
            f.write(f"| Health Check | {c.get('health', '-')} |\n")
            for msg in r.problems:
                f.write(f"| 문제 | {msg} |\n")
    return 0 if r.passed else 1


if __name__ == "__main__":
    sys.exit(main())
