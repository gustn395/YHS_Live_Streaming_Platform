"""failed_step 테스트. 원본: docs/testing/fe06-e2e-ci.md Q10(Step Summary의 실패 단계, S6 R4).

입력은 Workflow가 env로 넘기는 `<step id>=<outcome>` 목록(순서 = 실행 순서)이다. id와 outcome은 고정 토큰이다.
"""

import contextlib
import io
import os
import shutil
import tempfile
import unittest

import failed_step as fs


class FirstFailureTest(unittest.TestCase):
    def test_returns_first_failure_in_order(self):
        text = "prepare=success secrets=success compose=failure backend=skipped report=failure"
        self.assertEqual(fs.first_failure(text), ("compose", "failure"))

    def test_cancelled_counts_when_no_failure_before_it(self):
        self.assertEqual(fs.first_failure("prepare=success playwright=cancelled report=skipped"),
                         ("playwright", "cancelled"))

    def test_none_when_everything_succeeded_or_skipped(self):
        self.assertIsNone(fs.first_failure("prepare=success stub=skipped"))

    def test_empty_or_missing_input_is_unknown(self):
        self.assertIsNone(fs.first_failure(""))
        self.assertIsNone(fs.first_failure(None))

    def test_malformed_tokens_are_ignored_not_echoed(self):
        text = "::error::x=failure prepare=success bad compose=FAILURE backend=failure Backend=failure"
        self.assertEqual(fs.first_failure(text), ("backend", "failure"))


class MainTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, self.dir, True)
        self.summary = os.path.join(self.dir, "summary")

    def run_main(self, outcomes):
        env = {"GITHUB_STEP_SUMMARY": self.summary}
        if outcomes is not None:
            env["STEP_OUTCOMES"] = outcomes
        buf = io.StringIO()
        with contextlib.redirect_stdout(buf):
            code = fs.main(env)
        with open(self.summary, encoding="utf-8") as f:
            return code, buf.getvalue(), f.read()

    def test_writes_fixed_step_name_to_summary(self):
        code, out, summary = self.run_main("prepare=success backend=failure")
        self.assertEqual(code, 0)
        self.assertIn("| 실패 단계 | backend (failure) |", summary)
        self.assertIn("backend", out)

    def test_unknown_when_no_failed_step_is_found(self):
        _, _, summary = self.run_main("prepare=success")
        self.assertIn("| 실패 단계 | 확인 불가 |", summary)

    def test_missing_env_is_unknown(self):
        _, _, summary = self.run_main(None)
        self.assertIn("확인 불가", summary)

    def test_nothing_but_fixed_tokens_reaches_output(self):
        code, out, summary = self.run_main("::warning::boom=failure secrets=failure x=$(id)")
        self.assertEqual(code, 0)
        for text in (out, summary):
            self.assertNotIn("::warning", text)
            self.assertNotIn("$(id)", text)
        self.assertIn("secrets (failure)", summary)


if __name__ == "__main__":
    unittest.main()
