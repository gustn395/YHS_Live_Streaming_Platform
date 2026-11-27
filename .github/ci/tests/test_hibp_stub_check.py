"""hibp_stub_check 테스트. 원본: docs/testing/fe06-e2e-ci.md Q5(Backend가 스텁을 실제로 호출했다는 증거).

통계 파일은 스텁(frontend/e2e/support/hibpStub.ts)이 기동할 때 0으로 초기화하고 요청마다 원자적으로 다시 쓴다.
최소 정상 호출 수는 실행한 E2E 테스트 수다: 테스트마다 새 계정을 하나 만들고(createAccount), 가입마다 HIBP를 한 번 부른다.
"""

import contextlib
import io
import json
import os
import shutil
import tempfile
import unittest

import hibp_stub_check as hc


class CheckTest(unittest.TestCase):
    def test_enough_ok_and_no_unexpected_passes(self):
        self.assertTrue(hc.check({"ok": 2, "unexpected": 0, "health": 5}, 2).passed)
        self.assertTrue(hc.check({"ok": 3, "unexpected": 0, "health": 1}, 2).passed)

    def test_too_few_ok_fails(self):
        self.assertFalse(hc.check({"ok": 1, "unexpected": 0, "health": 9}, 2).passed)

    def test_health_is_not_counted_as_ok(self):
        self.assertFalse(hc.check({"ok": 0, "unexpected": 0, "health": 10}, 1).passed)

    def test_unexpected_request_fails(self):
        self.assertFalse(hc.check({"ok": 5, "unexpected": 1, "health": 1}, 2).passed)

    def test_malformed_stats_fail(self):
        for bad in (None, [], {}, {"ok": 2}, {"ok": "2", "unexpected": 0, "health": 0},
                    {"ok": -1, "unexpected": 0, "health": 0}, {"ok": True, "unexpected": 0, "health": 0},
                    {"ok": 2, "unexpected": 0.0, "health": 0}):
            self.assertFalse(hc.check(bad, 1).passed, bad)

    def test_min_ok_must_be_positive(self):
        self.assertFalse(hc.check({"ok": 0, "unexpected": 0, "health": 0}, 0).passed)


class MainTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, self.dir, True)
        self.summary = os.path.join(self.dir, "summary")

    def run_main(self, args):
        buf = io.StringIO()
        with contextlib.redirect_stdout(buf), contextlib.redirect_stderr(buf):
            code = hc.main(args, env={"GITHUB_STEP_SUMMARY": self.summary})
        return code, buf.getvalue()

    def stats_file(self, data, raw=False):
        path = os.path.join(self.dir, "stats.json")
        with open(path, "w", encoding="utf-8") as f:
            f.write(data if raw else json.dumps(data))
        return path

    def test_pass(self):
        code, out = self.run_main(["--stats-file", self.stats_file({"ok": 2, "unexpected": 0, "health": 3}),
                                   "--min-ok", "2"])
        self.assertEqual(code, 0)
        self.assertIn("ok=2", out)

    def test_missing_file_fails(self):
        code, _ = self.run_main(["--stats-file", os.path.join(self.dir, "none"), "--min-ok", "1"])
        self.assertEqual(code, 1)

    def test_invalid_json_fails(self):
        code, _ = self.run_main(["--stats-file", self.stats_file("{oops", raw=True), "--min-ok", "1"])
        self.assertEqual(code, 1)

    def test_bad_min_ok_fails(self):
        path = self.stats_file({"ok": 2, "unexpected": 0, "health": 0})
        for value in ("", "x", "-1", "0"):
            code, _ = self.run_main(["--stats-file", path, "--min-ok", value])
            self.assertEqual(code, 1, value)

    def test_summary_has_counts_only(self):
        self.run_main(["--stats-file", self.stats_file({"ok": 2, "unexpected": 0, "health": 3, "extra": "::x"}),
                       "--min-ok", "2"])
        with open(self.summary, encoding="utf-8") as f:
            text = f.read()
        self.assertIn("| 정상 호출 | 2 |", text)
        self.assertNotIn("::x", text)


if __name__ == "__main__":
    unittest.main()
