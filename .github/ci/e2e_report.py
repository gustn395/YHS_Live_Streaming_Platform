"""E2E CI 결과 검증·안전한 요약 (Python 3 표준 라이브러리만 사용).

정책 원본: docs/testing/fe06-e2e-ci.md (Q3·Q10·Q11), docs/frontend/decisions.md FE-D044 (2).
- 입력: 실행 전 `playwright test --list --reporter=json` 목록과 실행 결과 JSON(PLAYWRIGHT_JSON_OUTPUT_FILE).
- 필수 테스트: PR은 `@slow`가 없는 테스트 전부, FULL은 목록 전부. 테스트를 더해도 Workflow를 고치지 않는다.
- 실행 0건, skipped·flaky·unexpected, 필수 누락, 목록 밖 실행, PR의 @slow 실행, FULL의 @slow 미실행은 실패다.
- 출력은 개수·고정 문구만 쓴다. 테스트 제목·오류 원문·JSON 내용은 로그와 Step Summary에 남기지 않는다
  (단언 실제값에 OTP·쿠키가 들어갈 수 있다).
"""

import argparse
import json
import os
import sys
from dataclasses import dataclass, field

SCOPES = ("pr", "full")
SLOW_TAG = "slow"
COUNTERS = ("expected", "unexpected", "flaky", "skipped")


class ReportError(Exception):
    """결과 구조가 예상과 다르다(내용은 메시지에 넣지 않는다)."""


@dataclass
class Verdict:
    passed: bool
    executed: int = 0
    required: int = 0
    missing: int = 0
    extra: int = 0
    slow_executed: int = 0
    stats: dict = field(default_factory=dict)
    duration_s: float = 0.0
    problems: list = field(default_factory=list)  # 고정 문구만


def _specs(report):
    if not isinstance(report, dict) or not isinstance(report.get("suites"), list):
        raise ReportError("suites 없음")
    stack = list(report["suites"])
    while stack:
        suite = stack.pop()
        if not isinstance(suite, dict):
            raise ReportError("suite 형식")
        for spec in suite.get("specs") or []:
            if not isinstance(spec, dict) or not isinstance(spec.get("tests"), list):
                raise ReportError("spec 형식")
            yield spec
        children = suite.get("suites") or []
        if not isinstance(children, list):
            raise ReportError("suites 형식")
        stack.extend(children)


def _key(spec):
    """목록과 결과가 같은 방식으로 쓰는 테스트 식별자. 같은 파일에 같은 제목이 다른 describe에 있어도 위치·id로 구분한다."""
    sid, file, line, column, title = (spec.get(k) for k in ("id", "file", "line", "column", "title"))
    if not all(isinstance(v, str) for v in (sid, file, title)):
        raise ReportError("spec 식별자")
    if not all(isinstance(v, int) and not isinstance(v, bool) for v in (line, column)):
        raise ReportError("spec 위치")
    return sid, file, line, column, title


def _is_slow(spec):
    tags = spec.get("tags") or []
    if not isinstance(tags, list):
        raise ReportError("tags 형식")
    return SLOW_TAG in tags or f"@{SLOW_TAG}" in tags


def _stats(report):
    if not isinstance(report, dict):
        raise ReportError("report 형식")
    stats = report.get("stats")
    if not isinstance(stats, dict):
        raise ReportError("stats 없음")
    out = {}
    for key in COUNTERS:
        value = stats.get(key, 0)
        if not isinstance(value, int) or isinstance(value, bool) or value < 0:
            raise ReportError("stats 값")
        out[key] = value
    duration = stats.get("duration", 0)
    return out, float(duration) / 1000 if isinstance(duration, (int, float)) else 0.0


def judge(scope, listing, result):
    if scope not in SCOPES:
        return Verdict(False, problems=["알 수 없는 실행 범위"])
    try:
        listed = {_key(s): _is_slow(s) for s in _specs(listing)}
        stats, duration = _stats(result)
        executed, bad_status = set(), 0
        for s in _specs(result):
            for test in s["tests"]:
                if not isinstance(test, dict):
                    raise ReportError("test 형식")
                results = test.get("results")
                if test.get("status") != "expected" or not isinstance(results, list) or not results:
                    bad_status += 1
                else:
                    executed.add(_key(s))
    except ReportError:
        return Verdict(False, problems=["결과 구조를 해석할 수 없음"])

    required = {k for k, slow in listed.items() if scope == "full" or not slow}
    v = Verdict(True, executed=len(executed), required=len(required), stats=stats, duration_s=duration)
    v.missing = len(required - executed)
    v.extra = len(executed - required)
    v.slow_executed = sum(1 for k in executed if listed.get(k))
    checks = [
        (v.executed == 0, "실행한 테스트가 0건"),
        (stats["unexpected"] > 0 or stats["flaky"] > 0 or stats["skipped"] > 0, "unexpected·flaky·skipped 있음"),
        (bad_status > 0, "기대 통과가 아닌 테스트 있음"),
        (v.missing > 0, "필수 테스트 누락"),
        (v.extra > 0, "목록 밖 또는 범위 밖 테스트 실행"),
        (scope == "pr" and v.slow_executed > 0, "PR에서 @slow 실행"),
        (scope == "full" and v.slow_executed == 0, "FULL에서 @slow 미실행"),
    ]
    v.problems = [msg for bad, msg in checks if bad]
    v.passed = not v.problems
    return v


def _load(path):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except (OSError, ValueError, UnicodeDecodeError):
        return None


def main(argv=None, env=None):
    env = os.environ if env is None else env
    p = argparse.ArgumentParser(add_help=False)
    p.add_argument("--scope", required=True)
    p.add_argument("--list-file", required=True)
    p.add_argument("--result-file", required=True)
    try:
        args = p.parse_args(argv)
    except SystemExit:
        print("e2e-report: 인자 오류")
        return 1
    scope = args.scope if args.scope in SCOPES else "unknown"
    listing, result = _load(args.list_file), _load(args.result_file)
    if listing is None or result is None:
        v = Verdict(False, problems=["목록 또는 결과 파일을 읽을 수 없음"])
    else:
        v = judge(args.scope, listing, result)
    s = v.stats
    print(f"e2e-report: scope={scope} passed={v.passed} executed={v.executed} required={v.required} "
          f"missing={v.missing} extra={v.extra} slow={v.slow_executed} "
          + " ".join(f"{k}={s.get(k, '-')}" for k in COUNTERS))
    for msg in v.problems:
        print(f"e2e-report: 문제 {msg}")
    output = env.get("GITHUB_OUTPUT")
    if output:
        # executed는 HIBP 스텁 최소 정상 호출 수로 쓴다(테스트마다 계정 1개 가입, fe06 Q5)
        with open(output, "a", encoding="utf-8") as f:
            f.write(f"executed={v.executed}\npassed={'true' if v.passed else 'false'}\n")
    summary = env.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as f:
            f.write("### E2E 결과\n\n| 항목 | 값 |\n| --- | --- |\n")
            f.write(f"| 범위 | {scope} |\n| 판정 | {'PASS' if v.passed else 'FAIL'} |\n")
            f.write(f"| 실행 | {v.executed} |\n| 필수 | {v.required} |\n| 누락 | {v.missing} |\n")
            f.write(f"| 범위 밖 실행 | {v.extra} |\n| @slow 실행 | {v.slow_executed} |\n")
            for k in COUNTERS:
                f.write(f"| {k} | {s.get(k, '-')} |\n")
            f.write(f"| 소요(초) | {v.duration_s:.1f} |\n")
            for msg in v.problems:
                f.write(f"| 문제 | {msg} |\n")
            f.write("\n테스트 제목·오류 원문은 공개하지 않는다(fe06 Q10).\n")
    return 0 if v.passed else 1


if __name__ == "__main__":
    sys.exit(main())
