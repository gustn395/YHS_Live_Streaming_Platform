"""E2E CI 실패 진단 로그 필터 (Python 3 표준 라이브러리만 사용).

정책 원본: docs/testing/fe06-e2e-ci.md Q10.
- Allowlist가 먼저다: 알려진 형식(Spring Boot 로그 줄, 엄격한 stack frame, 예외 머리줄, `docker compose logs` 줄,
  Playwright list reporter의 결과·집계 줄)만 출력하고, 그 밖의 줄은 내용 없이 `[비공개 줄 N개]`로 묶는다.
  Playwright 오류 원문(단언 실제값·코드 조각·호출 로그)은 모두 가린다.
- 허용한 줄의 메시지에도 민감 값 패턴(JWT, 긴 토큰·키, 비밀 이름의 값, 쿠키·인증 헤더, OTP, 이메일)을 가린다.
  패턴 필터만으로 보호가 끝났다고 보지 않는다. 예외 메시지와 postgres DETAIL·STATEMENT는 값이 들어갈 수 있어 출력하지 않는다.
- 모든 출력 줄 앞에 고정 접두어를 두고 `::`를 끊어, 줄이 워크플로 명령으로 해석되지 않게 한다.
- 파일이 없거나, 이진 데이터이거나, UTF-8이 아니면 아무 줄도 출력하지 않는다.
"""

import argparse
import re
import sys

PREFIX = "log| "
WITHHELD = PREFIX + "[비공개 줄]"
MAX_TAIL = 1000

_ANSI = re.compile(r"\x1b\[[0-9;?]*[ -/]*[@-~]")
_CONTROL = re.compile(r"[\x00-\x08\x0b-\x1f\x7f]")

_SPRING = re.compile(
    r"^(?P<head>\d{4}-\d{2}-\d{2}T[\d:.+\-Z]+\s+(?:TRACE|DEBUG|INFO|WARN|ERROR)\s+\d+\s+---\s*(?:\[[^\]]*\]\s*)+)"
    r"(?P<logger>[\w.$-]+)\s+:\s(?P<msg>.*)$")
_FRAME = re.compile(r"^\s+at [\w.$/<>-]+\((?:[\w$.-]+\.java(?::\d+)?|Native Method|Unknown Source)\)$")
_MORE = re.compile(r"^\s+\.\.\. \d+ (?:more|common frames omitted)$")
_EXCEPTION = re.compile(
    r"^(?P<lead>(?:Caused by|Suppressed): )?(?P<cls>[A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)+"
    r"(?:Exception|Error|Throwable))(?P<rest>:.*)?$")
_COMPOSE = re.compile(r"^(?P<svc>[\w.-]+)\s+\|\s?(?P<msg>.*)$")
# Playwright list reporter: 시작 줄, 테스트 결과 줄, 실패 머리줄, 실패 목록 줄, 집계 줄만 허용한다(제목은 코드에 있는 고정 문자열).
_PW_TEST = r"\[[\w-]+\] › [\w./\\-]+:\d+:\d+ › .+?"
_PLAYWRIGHT = [
    re.compile(r"^Running \d+ tests? using \d+ workers?$"),
    re.compile(rf"^(?:✓|✘|-|°|ok|x)\s+\d+ {_PW_TEST}(?: \([\d.]+(?:ms|s|m|h)\))?$"),
    re.compile(rf"^\d+\) {_PW_TEST}[\s─]*$"),
    re.compile(rf"^{_PW_TEST}$"),
    re.compile(r"^\d+ (?:passed|failed|flaky|skipped|did not run|interrupted)(?: \([\d.]+(?:ms|s|m|h)\))?$"),
]
_PG_WITHHELD = re.compile(r"\b(?:DETAIL|STATEMENT|HINT|CONTEXT|QUERY):")

_REDACTIONS = [
    (re.compile(r"eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]*"), "<가림>"),
    (re.compile(r"(?i)\b(authorization|set-cookie|cookie)\b\s*[:=].*$"), r"\1: <가림>"),
    (re.compile(r"(?i)\b(\w*(?:password|passwd|pwd|secret|token|otp|code|pepper|hmac|key|credential|session)\w*)"
                r"\s*[:=]\s*\S+"), r"\1=<가림>"),
    (re.compile(r"(?i)(인증\s*번호|otp)(\D{0,10})\d{4,8}"), r"\1\2<가림>"),
    (re.compile(r"[\w.+-]+@[\w-]+(?:\.[\w-]+)+"), "<이메일>"),
]
_LONG_TOKEN = re.compile(r"[A-Za-z0-9+/_-]{20,}={0,2}")
_IDENTIFIER = re.compile(r"[A-Z][a-z0-9]+(?:[A-Z][a-z0-9]*)+")


def _clean(text):
    return _CONTROL.sub("", _ANSI.sub("", text.replace("\t", " ")))


def _long_token(match):
    token = match.group(0)
    return token if _IDENTIFIER.fullmatch(token) else "<가림>"


def redact(text):
    for pattern, repl in _REDACTIONS:
        text = pattern.sub(repl, text)
    return _LONG_TOKEN.sub(_long_token, text)


def _emit(text):
    return PREFIX + text.replace("::", ": :")


def filter_line(line, kind):
    raw = line.rstrip("\r\n")
    line = _clean(raw)
    if kind == "spring":
        m = _SPRING.match(line)
        if m:
            return _emit(f"{m['head']}{m['logger']} : {redact(m['msg'])}")
        if _FRAME.match(raw.replace("\t", " ")) or _MORE.match(line):
            return _emit(line.strip())
        m = _EXCEPTION.match(line.strip())
        if m:
            tail = ": <메시지 생략>" if m["rest"] else ""
            return _emit(f"{m['lead'] or ''}{m['cls']}{tail}")
        return WITHHELD
    if kind == "compose":
        m = _COMPOSE.match(line)
        if not m:
            return WITHHELD
        if _PG_WITHHELD.search(m["msg"]):
            return _emit(f"{m['svc']} | <비공개: 값이 들어갈 수 있는 줄>")
        return _emit(f"{m['svc']} | {redact(m['msg'])}")
    if kind == "playwright":
        text = line.strip()
        if any(p.match(text) for p in _PLAYWRIGHT):
            return _emit(redact(text.rstrip("─ ")))
        return WITHHELD
    raise ValueError("kind")


def _read_lines(path):
    try:
        if path == "-":
            data = sys.stdin.read().encode("utf-8", "surrogateescape")
        else:
            with open(path, "rb") as f:
                data = f.read()
    except OSError:
        return None
    if b"\x00" in data:
        return None
    try:
        return data.decode("utf-8").splitlines()
    except UnicodeDecodeError:
        return None


def main(argv=None):
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")  # 콘솔 기본 인코딩(예: cp949)에 기대지 않는다
    p = argparse.ArgumentParser(add_help=False)
    p.add_argument("--kind", choices=("spring", "compose", "playwright"), required=True)
    p.add_argument("--tail", type=int, required=True)
    p.add_argument("path")
    try:
        args = p.parse_args(argv)
    except SystemExit:
        print("log-filter: 인자 오류")
        return 2
    if not 1 <= args.tail <= MAX_TAIL:
        print("log-filter: tail 범위 오류")
        return 2
    lines = _read_lines(args.path)
    if lines is None:
        print("log-filter: 출력 안 함(파일 없음, 이진 데이터 또는 UTF-8 아님)")
        return 0
    tail = lines[-args.tail:]
    print(f"log-filter: kind={args.kind} lines={len(tail)} (Allowlist 형식만 출력, 값 가림)")
    withheld = 0
    for line in tail:
        if not _clean(line).strip():
            continue  # 빈 줄은 정보가 없다
        out = filter_line(line, args.kind)
        if out == WITHHELD:
            withheld += 1
            continue
        if withheld:
            print(f"{PREFIX}[비공개 줄 {withheld}개]")
            withheld = 0
        print(out)
    if withheld:
        print(f"{PREFIX}[비공개 줄 {withheld}개]")
    return 0


if __name__ == "__main__":
    sys.exit(main())
