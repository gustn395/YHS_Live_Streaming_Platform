"""Backend·Frontend·E2E CI 공통 변경 감지 (Python 3 표준 라이브러리만 사용).

정책 원본: docs/testing/fe02-ci-split.md (1장 Q1~Q3·Q6·Q7, 2~3장), E2E 영역은 docs/testing/fe06-e2e-ci.md (Q2·3장).
- 분류 규칙은 이 파일 한 곳에만 둔다. 세 Workflow(backend·frontend·e2e)가 같은 결과를 각자 읽는다.
- e2e는 backend·frontend 중 하나라도 실행하거나 E2E 전용 경로가 바뀌면 실행한다.
- 입력은 환경변수로만 받는다(EVENT_NAME·BASE_SHA·HEAD_SHA). PR에서 온 값을 셸에 넣지 않는다.
- 출력은 고정 토큰만 쓴다. 파일 경로는 Job 로그에 repr로만 남기고 Step Summary에는 넣지 않는다.
"""

import os
import re
import subprocess
import sys
from collections import Counter
from dataclasses import dataclass, field

SHA_RE = re.compile(r"[0-9a-f]{40}")

# 위에서부터 처음 맞는 규칙을 쓴다(fe02-ci-split.md 3장).
_EXACT = {
    ".github/workflows/backend-ci.yml": "backend",
    ".github/workflows/frontend-ci.yml": "frontend",
    ".github/workflows/e2e-ci.yml": "e2e",
}
_PREFIX = [
    (".github/", "both"),
    ("infrastructure/docker/e2e/", "e2e"),
    ("backend/", "backend"),
    ("frontend/", "frontend"),
    ("docs/api/", "both"),
    ("docs/", "none"),
]
_ROOT_SHARED = {".gitignore", ".env.example", ".coderabbit.yaml"}


def classify(path):
    """경로 하나를 backend·frontend·e2e·both·none 중 하나로 분류한다."""
    if path in _EXACT:
        return _EXACT[path]
    for prefix, area in _PREFIX:
        if path.startswith(prefix):
            return area
    if "/" not in path and path.endswith(".md"):
        return "none"
    if path in _ROOT_SHARED:
        return "both"  # 규칙 9: 공통 설정임을 명시한다(결과는 규칙 10과 같다)
    return "both"  # 규칙 10: 분류되지 않은 경로는 양쪽을 실행한다


def split_z(data):
    """`git diff -z` 출력(NUL 구분)을 경로 목록으로 바꾼다."""
    return [p.decode("utf-8", "surrogateescape") for p in data.split(b"\0") if p]


@dataclass
class Result:
    backend: str
    frontend: str
    e2e: str
    mode: str
    reason: str
    classified: list = field(default_factory=list)  # [(경로, 분류)]
    detail: str = ""


def _all(mode, reason, detail=""):
    return Result("run", "run", "run", mode, reason, detail=detail)


def git_runner(cwd=None):
    def run(args):
        p = subprocess.run(["git", *args], cwd=cwd, capture_output=True, timeout=120)
        return p.returncode, p.stdout, p.stderr
    return run


def decide(event_name, base_sha, head_sha, run_git=None):
    run_git = run_git or git_runner()
    if event_name == "push":
        return _all("FULL", "push_main")
    if event_name == "workflow_dispatch":
        return _all("FULL", "dispatch")
    if event_name != "pull_request":
        return _all("FALLBACK", "unknown_event")
    if not (SHA_RE.fullmatch(base_sha or "") and SHA_RE.fullmatch(head_sha or "")):
        return _all("FALLBACK", "invalid_input")
    try:
        code, out, err = run_git(["merge-base", base_sha, head_sha])
        if code != 0:
            return _all("FALLBACK", "git_error", f"merge-base exit {code}: {err[:200]!r}")
        merge_base = out.decode("ascii", "replace").strip()
        if not SHA_RE.fullmatch(merge_base):
            return _all("FALLBACK", "git_error", "merge-base output is not a SHA")
        code, out, err = run_git(["diff", "--name-only", "--no-renames", "-z", merge_base, head_sha, "--"])
        if code != 0:
            return _all("FALLBACK", "git_error", f"diff exit {code}: {err[:200]!r}")
    except (OSError, subprocess.SubprocessError) as e:
        return _all("FALLBACK", "git_error", f"git not runnable: {type(e).__name__}")
    paths = split_z(out)
    if not paths:
        return _all("FALLBACK", "empty_diff")
    classified = [(p, classify(p)) for p in paths]
    areas = {a for _, a in classified}
    backend = "run" if areas & {"backend", "both"} else "skip"
    frontend = "run" if areas & {"frontend", "both"} else "skip"
    e2e = "run" if "run" in (backend, frontend) or "e2e" in areas else "skip"
    if backend == frontend == e2e == "skip":
        return Result("skip", "skip", "skip", "NORMAL", "docs_only", classified)
    return Result(backend, frontend, e2e, "NORMAL", "changed", classified)


def main(env=None, run_git=None):
    env = os.environ if env is None else env
    output = env.get("GITHUB_OUTPUT")
    if not output:
        print("GITHUB_OUTPUT 없음: 감지 결과를 쓸 수 없다", file=sys.stderr)
        return 1
    r = decide(env.get("EVENT_NAME", ""), env.get("BASE_SHA", ""), env.get("HEAD_SHA", ""), run_git)
    # 로그: 줄 머리에 고정 문자열을 둬 경로가 워크플로 명령(::)으로 해석되지 않게 한다.
    print(f"detect: mode={r.mode} reason={r.reason} backend={r.backend} frontend={r.frontend} e2e={r.e2e}")
    if r.detail:
        print(f"detect: detail {r.detail}")
    for path, area in r.classified:
        print(f"detect: {area:8} {path!r}")
    with open(output, "a", encoding="utf-8") as f:
        f.write(f"backend={r.backend}\nfrontend={r.frontend}\ne2e={r.e2e}\nmode={r.mode}\nreason={r.reason}\n")
    summary = env.get("GITHUB_STEP_SUMMARY")
    if summary:
        counts = Counter(a for _, a in r.classified)
        with open(summary, "a", encoding="utf-8") as f:
            f.write("### 변경 감지\n\n| 항목 | 값 |\n| --- | --- |\n")
            f.write(f"| mode | {r.mode} |\n| reason | {r.reason} |\n")
            f.write(f"| backend | {r.backend} |\n| frontend | {r.frontend} |\n| e2e | {r.e2e} |\n")
            for area in ("backend", "frontend", "e2e", "both", "none"):
                f.write(f"| 경로 수({area}) | {counts.get(area, 0)} |\n")
            f.write("\n경로 목록은 Job 로그에 있다.\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
