"""npm audit 결과 판정과 Step Summary 기록 (Python 3 표준 라이브러리만 사용).

정책 원본: docs/frontend/decisions.md FE-D017(차단 기준)·FE-D019(실행 오류 fail-closed).
- blocking: `npm audit --omit=dev --audit-level=high --json` 결과. PASS 외에는 exit 1.
  취약점(AUDIT_VULN)과 실행 오류(AUDIT_ERROR)를 구분한다. 재시도하지 않는다.
- report: 전체 `npm audit --json` 결과. 항상 exit 0. 오류는 AUDIT_ERROR로 적고 0건으로 적지 않는다.
필수 필드가 없거나 값이 잘못되면 AUDIT_ERROR다.
"""

import argparse
import json
import os
import sys
from dataclasses import dataclass

SEVERITIES = ("info", "low", "moderate", "high", "critical")
NOTE = "`--omit=dev`는 운영 의존성 트리만 감사한다. 번들에 실제로 들어간 코드를 분석하지 않는다."


@dataclass
class AuditVerdict:
    verdict: str  # PASS·AUDIT_VULN·FOUND·AUDIT_ERROR
    counts: dict | None
    detail: str


def _error(detail):
    return AuditVerdict("AUDIT_ERROR", None, detail)


def _counts(data):
    if not isinstance(data, dict):
        return None, "JSON 최상위가 객체가 아니다"
    if "error" in data:
        return None, "npm audit가 error를 보고했다"
    if data.get("auditReportVersion") != 2:
        return None, "auditReportVersion이 2가 아니다"
    meta = data.get("metadata")
    vulns = meta.get("vulnerabilities") if isinstance(meta, dict) else None
    if not isinstance(vulns, dict):
        return None, "metadata.vulnerabilities가 없다"
    counts = {}
    for key in (*SEVERITIES, "total"):
        v = vulns.get(key)
        if type(v) is not int or v < 0:
            return None, f"metadata.vulnerabilities.{key} 값이 잘못됐다"
        counts[key] = v
    if counts["total"] != sum(counts[s] for s in SEVERITIES):
        return None, "total이 심각도 합과 다르다"
    return counts, ""


def judge(exit_code, raw, mode):
    if exit_code is None:
        return _error("종료 코드가 없다")
    try:
        data = json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError):
        return _error("JSON이 아니다")
    counts, why = _counts(data)
    if counts is None:
        return _error(why)
    if mode == "blocking":
        blocking = counts["high"] + counts["critical"]
        if (blocking > 0) != (exit_code != 0):
            return _error(f"종료 코드 {exit_code}와 high·critical {blocking}건이 맞지 않는다")
        if blocking:
            return AuditVerdict("AUDIT_VULN", counts, f"high·critical {blocking}건")
        return AuditVerdict("PASS", counts, "high·critical 0건")
    if exit_code != 0 and counts["total"] == 0:
        return _error(f"종료 코드 {exit_code}인데 취약점 0건")
    if counts["total"]:
        return AuditVerdict("FOUND", counts, f"취약점 {counts['total']}건(비차단)")
    return AuditVerdict("PASS", counts, "취약점 0건")


def _read_exit_code(path):
    try:
        with open(path, encoding="utf-8") as f:
            text = f.read().strip()
    except OSError:
        return None
    return int(text) if text.isdigit() else None


def main(argv=None, env=None):
    env = os.environ if env is None else env
    ap = argparse.ArgumentParser()
    ap.add_argument("--mode", choices=["blocking", "report"], required=True)
    ap.add_argument("--json-file", required=True)
    ap.add_argument("--exit-code-file", required=True)
    a = ap.parse_args(argv)
    try:
        with open(a.json_file, "rb") as f:
            raw = f.read()
    except OSError:
        raw = b""
    v = judge(_read_exit_code(a.exit_code_file), raw, a.mode)
    title = "차단 audit(--omit=dev --audit-level=high)" if a.mode == "blocking" else "전체 audit(비차단)"
    print(f"npm-audit: mode={a.mode} verdict={v.verdict} detail={v.detail}")
    if v.verdict == "AUDIT_ERROR":
        print(f"::error title=npm audit::{title} 실행 오류(취약점 판정 아님)" if a.mode == "blocking"
              else f"::warning title=npm audit::{title} 실행 오류")
    summary = env.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as f:
            f.write(f"### {title}: {v.verdict}\n\n{v.detail}\n\n")
            if v.counts is None:
                f.write("심각도별 개수: 확인 불가(실행 오류). 0건으로 보지 않는다.\n\n")
            else:
                f.write("| 심각도 | 건수 |\n| --- | --- |\n")
                for key in (*SEVERITIES, "total"):
                    f.write(f"| {key} | {v.counts[key]} |\n")
                f.write("\n")
            f.write(NOTE + "\n\n")
    if a.mode == "report":
        return 0
    return 0 if v.verdict == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())
