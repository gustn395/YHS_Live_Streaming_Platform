"""log_filter 테스트. 원본: docs/testing/fe06-e2e-ci.md Q10(실패 로그: Allowlist 우선, 안전하지 않으면 출력 안 함)."""

import contextlib
import io
import os
import shutil
import tempfile
import unittest

import log_filter as lf

JWT = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.c2lnbmF0dXJlLXZhbHVl"
KEY = "q3Vb0Zk1u8mX2pT6yR4nW9sL5aH7cE0dJ1fG3iK8oP4="
SPRING = "2026-11-28T00:40:01.123+09:00  WARN 7 --- [yhs] [nio-18080-exec-1] c.y.l.member.app.otp.OtpService          : "


class SpringLineTest(unittest.TestCase):
    def f(self, line):
        return lf.filter_line(line, "spring")

    def test_keeps_header_and_safe_message(self):
        out = self.f(SPRING + "OTP 메일 발송 실패: memberId=42, stage=SEND, compensated=true, cause=MailSendException")
        self.assertIn("WARN", out)
        self.assertIn("c.y.l.member.app.otp.OtpService", out)
        self.assertIn("memberId=42", out)
        self.assertIn("MailSendException", out)

    def test_redacts_sensitive_values_in_message(self):
        cases = [f"token={JWT}", f"Authorization: Bearer {JWT}", f"key {KEY}", "password=hunter22secret",
                 "Cookie: __Host-yhs_at=abc.def; XSRF-TOKEN=zzz", "인증 번호: 1234", "otp=5678",
                 "secret: s3cr3t-value", "e2e_ab12cd@e2e.test 로그인", "a" * 40, "0123456789abcdef0123456789abcdef"]
        for msg in cases:
            out = self.f(SPRING + msg)
            for leaked in (JWT, KEY, "hunter22secret", "abc.def", "zzz", "1234", "5678", "s3cr3t-value",
                           "e2e_ab12cd@e2e.test", "a" * 40, "0123456789abcdef0123456789abcdef"):
                self.assertNotIn(leaked, out, msg)

    def test_short_segment_jwt_is_redacted(self):
        short = "eyJhbGciOi.eyJzdWIi.c2lnbg"  # 세그먼트가 20자 미만이라 긴 토큰 규칙에 걸리지 않는다
        self.assertNotIn(short, self.f(SPRING + "응답 " + short))

    def test_stack_frame_kept_when_strict(self):
        frame = "\tat com.yhs.livestreaming.member.app.otp.OtpService.send(OtpService.java:140)"
        self.assertIn("OtpService.java:140", self.f(frame))

    def test_suspicious_stack_frame_withheld(self):
        self.assertEqual(self.f("\tat x.Y.z(token=" + JWT + ")"), lf.WITHHELD)

    def test_exception_header_keeps_class_only(self):
        out = self.f("Caused by: org.postgresql.util.PSQLException: ERROR: duplicate key value (email)=(a@b.c)")
        self.assertIn("org.postgresql.util.PSQLException", out)
        self.assertNotIn("a@b.c", out)
        self.assertNotIn("duplicate key", out)

    def test_unknown_format_withheld(self):
        for line in ("Set-Cookie: __Host-yhs_at=" + JWT, "random text with 1234", "{\"password\":\"x\"}"):
            self.assertEqual(self.f(line), lf.WITHHELD, line)


class ComposeLineTest(unittest.TestCase):
    def f(self, line):
        return lf.filter_line(line, "compose")

    def test_keeps_service_and_redacted_message(self):
        out = self.f("postgres-1  | 2026-11-27 15:40:01.000 UTC [1] LOG:  database system is ready to accept connections")
        self.assertIn("postgres-1", out)
        self.assertIn("ready to accept connections", out)

    def test_postgres_detail_and_statement_withheld(self):
        for kind in ("DETAIL:  Key (email)=(e2e_x@e2e.test) already exists.", "STATEMENT:  insert into member values ('x')"):
            out = self.f(f"postgres-1  | 2026-11-27 15:40:01.000 UTC [77] {kind}")
            self.assertNotIn("e2e_x@e2e.test", out)
            self.assertNotIn("insert into", out)

    def test_redacts_in_message(self):
        out = self.f(f"redis-1  | 1:M 27 Nov 2026 password={KEY}")
        self.assertNotIn(KEY, out)

    def test_non_compose_line_withheld(self):
        self.assertEqual(self.f("no separator here 1234"), lf.WITHHELD)


class PlaywrightLineTest(unittest.TestCase):
    """list reporter 출력: 결과·집계 줄만 남기고 오류 원문(단언 실제값·코드 조각·호출 로그)은 가린다."""

    def f(self, line):
        return lf.filter_line(line, "playwright")

    def test_keeps_result_and_summary_lines(self):
        for line in ("Running 2 tests using 1 worker",
                     "  ✓  1 [chromium] › login.spec.ts:29:1 › E2E-01 일반 로그인 @smoke (7.7s)",
                     "  ✘  2 [chromium] › login.spec.ts:48:1 › E2E-02 OTP 오입력 (6.0s)",
                     "  -  3 [chromium] › login.spec.ts:66:1 › E2E-03 재발송 @slow",
                     "  1) [chromium] › login.spec.ts:48:1 › E2E-02 OTP 오입력 ─────────",
                     "    [chromium] › login.spec.ts:48:1 › E2E-02 OTP 오입력",
                     "  1 failed", "  2 passed (20.1s)", "  1 flaky", "  1 skipped", "  1 did not run", "  3 passed (1.4m)"):
            out = self.f(line)
            self.assertNotEqual(out, lf.WITHHELD, line)

    def test_failure_header_drops_box_drawing(self):
        self.assertNotIn("─", self.f("  1) [chromium] › login.spec.ts:48:1 › E2E-02 OTP ─────"))

    def test_error_details_withheld(self):
        for line in ("    Error: expect(received).toBe(expected)", '    Expected: "1234"', '    Received: "5678"',
                     "      > 52 |   await enterOtp(page, otp);", "    Call log:", "      - waiting for getByLabel('인증 번호')",
                     "    at /home/runner/work/x/frontend/e2e/login.spec.ts:52:3",
                     "    Error: 테스트 계정 가입 실패: 503", "E2E 환경 미기동: Backend에 연결할 수 없습니다(http://localhost:4173)"):
            self.assertEqual(self.f(line), lf.WITHHELD, line)

    def test_result_line_with_value_like_text_is_redacted(self):
        out = self.f(f"  ✓  1 [chromium] › login.spec.ts:29:1 › E2E-01 {JWT} (1.0s)")
        self.assertNotIn(JWT, out)


class OutputSafetyTest(unittest.TestCase):
    def test_every_output_line_is_prefixed_and_has_no_workflow_command(self):
        for line in ("::error::boom", SPRING + "::set-output name=x::y", "\x1b[31mred\x1b[0m",
                     "  ✓  1 [chromium] › a.spec.ts:1:1 › ::error::x (1s)"):
            for kind in ("spring", "compose", "playwright"):
                out = lf.filter_line(line, kind)
                self.assertTrue(out.startswith(lf.PREFIX), out)
                self.assertNotIn("::", out)
                self.assertNotIn("\x1b", out)

    def test_control_characters_removed(self):
        out = lf.filter_line(SPRING + "a\rb\x00c\x07", "spring")
        for ch in ("\r", "\x00", "\x07"):
            self.assertNotIn(ch, out)


class MainTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, self.dir, True)

    def run_main(self, args):
        buf = io.StringIO()
        with contextlib.redirect_stdout(buf), contextlib.redirect_stderr(buf):
            code = lf.main(args)
        return code, buf.getvalue()

    def write(self, data, mode="w"):
        path = os.path.join(self.dir, "log")
        with open(path, mode, **({} if "b" in mode else {"encoding": "utf-8"})) as f:
            f.write(data)
        return path

    def test_tail_limits_lines(self):
        path = self.write("".join(f"{SPRING}line {i}\n" for i in range(300)))
        code, out = self.run_main(["--kind", "spring", "--tail", "200", path])
        self.assertEqual(code, 0)
        body = [l for l in out.splitlines() if l.startswith(lf.PREFIX)]
        self.assertEqual(len(body), 200)
        self.assertIn("line 299", out)
        self.assertNotIn("line 99\n", out + "\n")

    def test_missing_file_prints_nothing_unsafe(self):
        code, out = self.run_main(["--kind", "spring", "--tail", "10", os.path.join(self.dir, "none")])
        self.assertEqual(code, 0)
        self.assertIn("출력 안 함", out)

    def test_binary_file_not_printed(self):
        path = self.write(b"\x00\x01\x02" + JWT.encode() + b"\xff\xfe", "wb")
        code, out = self.run_main(["--kind", "spring", "--tail", "10", path])
        self.assertEqual(code, 0)
        self.assertNotIn(JWT, out)
        self.assertIn("출력 안 함", out)

    def test_consecutive_withheld_lines_are_collapsed(self):
        path = self.write("Running 1 test using 1 worker\n    Expected: 1\n    Received: 2\n    at x\n  1 failed\n")
        code, out = self.run_main(["--kind", "playwright", "--tail", "50", path])
        self.assertEqual(code, 0)
        body = [l for l in out.splitlines() if l.startswith(lf.PREFIX)]
        self.assertEqual(len(body), 3, body)
        self.assertIn("3개", body[1])
        self.assertNotIn("Received", out)

    def test_blank_lines_are_skipped_not_counted(self):
        path = self.write("\nRunning 1 test using 1 worker\n\n   \n  1 passed (1.0s)\n")
        code, out = self.run_main(["--kind", "playwright", "--tail", "50", path])
        self.assertEqual(code, 0)
        self.assertNotIn("비공개", out)

    def test_valid_utf8_with_nul_is_treated_as_binary(self):
        path = self.write((SPRING + "a\x00b\n").encode("utf-8"), "wb")
        code, out = self.run_main(["--kind", "spring", "--tail", "10", path])
        self.assertEqual(code, 0)
        self.assertIn("출력 안 함", out)
        self.assertNotIn("WARN", out)

    def test_stdin_mode(self):
        import sys
        old = sys.stdin
        sys.stdin = io.StringIO(f"postgres-1  | LOG:  ok {KEY}\n")
        try:
            code, out = self.run_main(["--kind", "compose", "--tail", "5", "-"])
        finally:
            sys.stdin = old
        self.assertEqual(code, 0)
        self.assertNotIn(KEY, out)
        self.assertIn("postgres-1", out)

    def test_bad_arguments_fail(self):
        code, _ = self.run_main(["--kind", "other", "--tail", "5", "-"])
        self.assertNotEqual(code, 0)
        code, _ = self.run_main(["--kind", "spring", "--tail", "0", "-"])
        self.assertNotEqual(code, 0)


if __name__ == "__main__":
    unittest.main()
