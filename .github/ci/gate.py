"""CI 최종 Gate 판정 (Python 3 표준 라이브러리만 사용).

정책 원본: docs/testing/fe02-ci-split.md (1장 Q1·Q6·Q12, 2장 Gate 판정표).
각 Gate는 BE·FE 양쪽 감지 출력의 조합이 맞는지 먼저 확인한 뒤 자기 영역의 build 결과를 판정한다.
입력은 환경변수로만 받는다.
"""

import os
import sys
from dataclasses import dataclass

TARGETS = {"run", "skip"}
FALLBACK_REASONS = {"git_error", "empty_diff", "invalid_input", "unknown_event"}
FULL_REASONS = {"push_main", "dispatch"}
BUILD_RESULTS = {"success", "failure", "cancelled", "skipped"}


@dataclass
class Verdict:
    status: str  # NORMAL·SKIPPED·FALLBACK·FULL·FAILED
    passed: bool
    message: str


def valid_combination(backend, frontend, mode, reason):
    if backend not in TARGETS or frontend not in TARGETS:
        return False
    both_run = backend == frontend == "run"
    if mode == "NORMAL":
        if reason == "changed":
            return not (backend == frontend == "skip")
        return reason == "docs_only" and backend == frontend == "skip"
    if mode == "FALLBACK":
        return reason in FALLBACK_REASONS and both_run
    if mode == "FULL":
        return reason in FULL_REASONS and both_run
    return False


def _fail(msg):
    return Verdict("FAILED", False, msg)


def judge(area, changes_result, backend, frontend, mode, reason, build_result):
    if area not in ("backend", "frontend"):
        return _fail(f"알 수 없는 Gate 영역: {area!r}")
    if changes_result != "success":
        return _fail(f"변경 감지 Job 결과가 success가 아니다: {changes_result!r}")
    if not valid_combination(backend, frontend, mode, reason):
        return _fail(f"감지 출력 조합이 맞지 않는다: backend={backend!r} frontend={frontend!r} "
                     f"mode={mode!r} reason={reason!r}")
    if build_result not in BUILD_RESULTS:
        return _fail(f"build 결과를 알 수 없다: {build_result!r}")
    target = backend if area == "backend" else frontend
    if target == "skip":
        if build_result == "skipped":
            return Verdict("SKIPPED", True, f"정상 생략(reason={reason})")
        return _fail(f"생략 대상인데 build 결과가 {build_result}")
    if build_result == "success":
        return Verdict(mode, True, f"build 실제 실행·성공(mode={mode}, reason={reason})")
    return _fail(f"build 결과 {build_result}(mode={mode}, reason={reason})")


def main(env=None):
    env = os.environ if env is None else env
    keys = ["GATE_AREA", "CHANGES_RESULT", "BACKEND_TARGET", "FRONTEND_TARGET", "DETECT_MODE", "DETECT_REASON",
            "BUILD_RESULT"]
    missing = [k for k in keys if k not in env]
    if missing:
        v = _fail(f"Gate 입력 누락: {', '.join(missing)}")
    else:
        v = judge(*(env[k] for k in keys))
    area = env.get("GATE_AREA", "?")
    print(f"gate: area={area!r} status={v.status} passed={v.passed} message={v.message}")
    if v.status == "FALLBACK":
        reason = env.get("DETECT_REASON", "")
        print(f"::warning title=CI Fallback::{area} Fallback build 실행(reason={reason})")
    elif not v.passed:
        print(f"::error title=CI Gate::{area} Gate FAILED")
    summary = env.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as f:
            f.write(f"### Gate {area}-ci: {v.status} ({'PASS' if v.passed else 'FAIL'})\n\n{v.message}\n")
    return 0 if v.passed else 1


if __name__ == "__main__":
    sys.exit(main())
