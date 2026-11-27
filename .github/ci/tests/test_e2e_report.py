"""e2e_report 테스트. 원본: docs/testing/fe06-e2e-ci.md (Q3·Q10·Q11), docs/frontend/decisions.md FE-D044 (2).

Playwright JSON reporter 구조(1.65): suites[].specs[]{title, file, tags(앞 @ 없음), tests[]{status, results[]}},
suites[].suites[](describe 중첩), stats{expected, unexpected, flaky, skipped, duration}.
"""

import contextlib
import io
import json
import os
import shutil
import tempfile
import unittest

import e2e_report as er

SECRET_ERROR = "Expected: \"1234\" Received: \"5678\" Cookie __Host-yhs_at=eyJhbGciOi.secret"


def spec(title, tags=(), status="expected", results=1, error=None):
    test = {"projectName": "chromium", "status": status,
            "results": [{"status": "passed" if status == "expected" else "failed",
                         "errors": [{"message": error}] if error else []} for _ in range(results)]}
    return {"title": title, "file": "login.spec.ts", "tags": list(tags), "tests": [test]}


def report(specs, nested=None, **stats):
    s = {"expected": 0, "unexpected": 0, "flaky": 0, "skipped": 0, "duration": 12345.6}
    s.update(stats)
    suite = {"title": "login.spec.ts", "file": "login.spec.ts", "specs": specs, "suites": nested or []}
    return {"config": {}, "suites": [suite], "errors": [], "stats": s}


LISTED = [spec("E2E-01 a @smoke", ["smoke"], "skipped", 0), spec("E2E-02 b", [], "skipped", 0),
          spec("E2E-03 c", ["slow"], "skipped", 0)]
LIST = report(LISTED, skipped=3)
PR_OK = report([spec("E2E-01 a @smoke", ["smoke"]), spec("E2E-02 b")], expected=2)
FULL_OK = report([spec("E2E-01 a @smoke", ["smoke"]), spec("E2E-02 b"), spec("E2E-03 c", ["slow"])], expected=3)


class JudgeTest(unittest.TestCase):
    def test_pr_passes_with_all_non_slow_tests(self):
        v = er.judge("pr", LIST, PR_OK)
        self.assertTrue(v.passed, v.problems)
        self.assertEqual(v.executed, 2)

    def test_full_passes_with_all_tests(self):
        v = er.judge("full", LIST, FULL_OK)
        self.assertTrue(v.passed, v.problems)
        self.assertEqual(v.executed, 3)

    def test_zero_executed_fails(self):
        self.assertFalse(er.judge("pr", LIST, report([])).passed)

    def test_unexpected_skipped_flaky_fail(self):
        for key in ("unexpected", "flaky", "skipped"):
            r = report([spec("E2E-01 a @smoke", ["smoke"]), spec("E2E-02 b")], expected=2, **{key: 1})
            self.assertFalse(er.judge("pr", LIST, r).passed, key)

    def test_test_level_status_other_than_expected_fails(self):
        for status in ("unexpected", "flaky", "skipped"):
            r = report([spec("E2E-01 a @smoke", ["smoke"]), spec("E2E-02 b", status=status)], expected=2)
            self.assertFalse(er.judge("pr", LIST, r).passed, status)

    def test_expected_status_without_results_fails(self):
        r = report([spec("E2E-01 a @smoke", ["smoke"]), spec("E2E-02 b", results=0)], expected=2)
        self.assertFalse(er.judge("pr", LIST, r).passed)

    def test_pr_must_not_run_slow(self):
        v = er.judge("pr", LIST, FULL_OK)
        self.assertFalse(v.passed)
        self.assertIn("PR에서 @slow 실행", v.problems)

    def test_empty_list_and_empty_result_fail(self):
        v = er.judge("pr", report([]), report([]))
        self.assertFalse(v.passed)
        self.assertIn("실행한 테스트가 0건", v.problems)

    def test_full_must_run_slow(self):
        self.assertFalse(er.judge("full", LIST, PR_OK).passed)

    def test_full_fails_when_no_slow_test_is_listed(self):
        no_slow = report([spec("E2E-01 a", [], "skipped", 0)], skipped=1)
        self.assertFalse(er.judge("full", no_slow, report([spec("E2E-01 a")], expected=1)).passed)

    def test_missing_required_test_fails(self):
        r = report([spec("E2E-01 a @smoke", ["smoke"])], expected=1)
        v = er.judge("pr", LIST, r)
        self.assertFalse(v.passed)
        self.assertEqual(v.missing, 1)

    def test_executed_test_not_in_list_fails(self):
        r = report([spec("E2E-01 a @smoke", ["smoke"]), spec("E2E-02 b"), spec("E2E-99 extra")], expected=3)
        self.assertFalse(er.judge("pr", LIST, r).passed)

    def test_new_untagged_test_becomes_required_without_workflow_change(self):
        listed = report(LISTED + [spec("E2E-04 new", [], "skipped", 0)], skipped=4)
        self.assertFalse(er.judge("pr", listed, PR_OK).passed)
        ok = report([spec("E2E-01 a @smoke", ["smoke"]), spec("E2E-02 b"), spec("E2E-04 new")], expected=3)
        self.assertTrue(er.judge("pr", listed, ok).passed)

    def test_nested_describe_suites_are_counted(self):
        r = report([spec("E2E-01 a @smoke", ["smoke"])], expected=2,
                   nested=[{"title": "d", "specs": [spec("E2E-02 b")], "suites": []}])
        self.assertTrue(er.judge("pr", LIST, r).passed)

    def test_unknown_scope_fails(self):
        self.assertFalse(er.judge("smoke", LIST, PR_OK).passed)

    def test_malformed_structures_fail(self):
        for bad in ({}, {"suites": "x", "stats": {}}, {"suites": [], "stats": None}, [], None):
            self.assertFalse(er.judge("pr", LIST, bad).passed, bad)
            self.assertFalse(er.judge("pr", bad, PR_OK).passed, bad)


class MainTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, self.dir, True)
        self.summary = os.path.join(self.dir, "summary")

    def write(self, name, data, raw=False):
        path = os.path.join(self.dir, name)
        with open(path, "w", encoding="utf-8") as f:
            f.write(data if raw else json.dumps(data))
        return path

    def run_main(self, scope, list_data, result_data, raw=False):
        import contextlib
        import io
        lp = self.write("list.json", list_data, raw)
        rp = self.write("result.json", result_data, raw)
        buf = io.StringIO()
        with contextlib.redirect_stdout(buf), contextlib.redirect_stderr(buf):
            code = er.main(["--scope", scope, "--list-file", lp, "--result-file", rp],
                           env={"GITHUB_STEP_SUMMARY": self.summary})
        return code, buf.getvalue()

    def test_pass_exit_zero(self):
        code, _ = self.run_main("pr", LIST, PR_OK)
        self.assertEqual(code, 0)

    def test_fail_exit_one(self):
        code, _ = self.run_main("full", LIST, PR_OK)
        self.assertEqual(code, 1)

    def test_missing_file_fails_without_traceback(self):
        import contextlib
        import io
        buf = io.StringIO()
        with contextlib.redirect_stdout(buf), contextlib.redirect_stderr(buf):
            code = er.main(["--scope", "pr", "--list-file", os.path.join(self.dir, "nope"),
                            "--result-file", os.path.join(self.dir, "nope2")], env={})
        self.assertEqual(code, 1)
        self.assertNotIn("Traceback", buf.getvalue())

    def test_invalid_json_fails(self):
        code, out = self.run_main("pr", "{not json", "{not json", raw=True)
        self.assertEqual(code, 1)
        self.assertNotIn("not json", out)

    def test_error_text_and_titles_never_printed(self):
        r = report([spec("E2E-01 a @smoke", ["smoke"]), spec("E2E-02 b", status="unexpected", error=SECRET_ERROR)],
                   expected=1, unexpected=1)
        code, out = self.run_main("pr", LIST, r)
        self.assertEqual(code, 1)
        with open(self.summary, encoding="utf-8") as f:
            summary = f.read()
        for text in (out, summary):
            self.assertNotIn("1234", text)
            self.assertNotIn("eyJ", text)
            self.assertNotIn("Cookie", text)
            self.assertNotIn("E2E-02 b", text)

    def test_summary_has_safe_counts(self):
        self.run_main("full", LIST, FULL_OK)
        with open(self.summary, encoding="utf-8") as f:
            summary = f.read()
        self.assertIn("| 범위 | full |", summary)
        self.assertIn("| 실행 | 3 |", summary)
        self.assertIn("| 판정 | PASS |", summary)

    def test_writes_executed_count_to_github_output(self):
        out_file = os.path.join(self.dir, "out")
        lp, rp = self.write("list.json", LIST), self.write("result.json", PR_OK)
        with contextlib.redirect_stdout(io.StringIO()):
            er.main(["--scope", "pr", "--list-file", lp, "--result-file", rp], env={"GITHUB_OUTPUT": out_file})
        with open(out_file, encoding="utf-8") as f:
            self.assertEqual(f.read().splitlines(), ["executed=2", "passed=true"])

    def test_failed_report_writes_passed_false(self):
        out_file = os.path.join(self.dir, "out")
        lp, rp = self.write("list.json", LIST), self.write("result.json", PR_OK)
        with contextlib.redirect_stdout(io.StringIO()):
            er.main(["--scope", "full", "--list-file", lp, "--result-file", rp], env={"GITHUB_OUTPUT": out_file})
        with open(out_file, encoding="utf-8") as f:
            self.assertIn("passed=false", f.read().splitlines())

    def test_workflow_command_like_values_are_not_echoed(self):
        code, out = self.run_main("::error::x", LIST, PR_OK)
        self.assertEqual(code, 1)
        self.assertNotIn("::error::x", out)


if __name__ == "__main__":
    unittest.main()
