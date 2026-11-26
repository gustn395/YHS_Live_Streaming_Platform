"""gate 판정 테스트. BE·FE 양쪽 감지 출력의 전체 조합을 곱집합으로 검사한다. 원본: docs/testing/fe02-ci-split.md."""

import itertools
import os
import shutil
import tempfile
import unittest

import gate

# gate.py와 독립적으로 적은 허용 조합. (backend, frontend, mode, reason)
VALID_COMBOS = {
    ("run", "run", "NORMAL", "changed"),
    ("run", "skip", "NORMAL", "changed"),
    ("skip", "run", "NORMAL", "changed"),
    ("skip", "skip", "NORMAL", "docs_only"),
    ("run", "run", "FALLBACK", "git_error"),
    ("run", "run", "FALLBACK", "empty_diff"),
    ("run", "run", "FALLBACK", "invalid_input"),
    ("run", "run", "FALLBACK", "unknown_event"),
    ("run", "run", "FULL", "push_main"),
    ("run", "run", "FULL", "dispatch"),
}

AREAS = ["backend", "frontend"]
CHANGES = ["success", "failure", "cancelled", "skipped", ""]
TARGETS = ["run", "skip", "", "RUN"]
MODES = ["NORMAL", "FALLBACK", "FULL", "", "normal", "SKIPPED"]
REASONS = ["changed", "docs_only", "git_error", "empty_diff", "invalid_input", "unknown_event",
           "push_main", "dispatch", "", "bogus"]
BUILDS = ["success", "failure", "cancelled", "skipped", ""]


def expected(area, changes, be, fe, mode, reason, build):
    """(passed, status) 참조 판정."""
    if changes != "success" or (be, fe, mode, reason) not in VALID_COMBOS:
        return False, "FAILED"
    target = be if area == "backend" else fe
    if target == "skip":
        return (True, "SKIPPED") if build == "skipped" else (False, "FAILED")
    return (True, mode) if build == "success" else (False, "FAILED")


class GateProductTest(unittest.TestCase):
    def test_full_cartesian_product(self):
        count = 0
        for args in itertools.product(AREAS, CHANGES, TARGETS, TARGETS, MODES, REASONS, BUILDS):
            v = gate.judge(*args)
            self.assertEqual((v.passed, v.status), expected(*args), args)
            count += 1
        self.assertEqual(count, 2 * 5 * 4 * 4 * 6 * 10 * 5)


class GateNamedCasesTest(unittest.TestCase):
    def j(self, area="frontend", changes="success", be="run", fe="run", mode="NORMAL", reason="changed",
          build="success"):
        return gate.judge(area, changes, be, fe, mode, reason, build)

    def test_contradiction_normal_changed_skip_skip_fails_on_both_gates(self):
        for area in AREAS:
            v = self.j(area=area, be="skip", fe="skip", build="skipped")
            self.assertFalse(v.passed, area)

    def test_fallback_with_a_skipped_side_fails(self):
        for area in AREAS:
            self.assertFalse(self.j(area=area, be="skip", mode="FALLBACK", reason="git_error",
                                    build="skipped").passed)

    def test_normal_skip_is_skipped_pass(self):
        v = self.j(area="backend", be="skip", fe="run", build="skipped")
        self.assertEqual((v.passed, v.status), (True, "SKIPPED"))

    def test_skip_but_build_ran_fails(self):
        self.assertFalse(self.j(area="backend", be="skip", build="success").passed)

    def test_fallback_build_success_is_fallback_pass(self):
        v = self.j(mode="FALLBACK", reason="empty_diff")
        self.assertEqual((v.passed, v.status), (True, "FALLBACK"))

    def test_fallback_build_failure_is_not_masked(self):
        self.assertFalse(self.j(mode="FALLBACK", reason="git_error", build="failure").passed)

    def test_run_but_build_skipped_is_abnormal(self):
        self.assertFalse(self.j(build="skipped").passed)

    def test_changes_cancelled_or_failed_blocks(self):
        for c in ("cancelled", "failure", "skipped"):
            self.assertFalse(self.j(changes=c).passed)

    def test_unknown_area_fails(self):
        self.assertFalse(gate.judge("both", "success", "run", "run", "NORMAL", "changed", "success").passed)

    def test_message_mentions_reason(self):
        v = self.j(mode="FALLBACK", reason="git_error")
        self.assertIn("git_error", v.message)


class GateMainTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, self.dir, True)
        self.summary = os.path.join(self.dir, "summary")

    def env(self, **kw):
        e = {"GATE_AREA": "frontend", "CHANGES_RESULT": "success", "BACKEND_TARGET": "skip",
             "FRONTEND_TARGET": "run", "DETECT_MODE": "NORMAL", "DETECT_REASON": "changed",
             "BUILD_RESULT": "success", "GITHUB_STEP_SUMMARY": self.summary}
        e.update(kw)
        return e

    def test_pass_exit_zero_and_summary(self):
        self.assertEqual(gate.main(env=self.env()), 0)
        with open(self.summary, encoding="utf-8") as f:
            self.assertIn("NORMAL", f.read())

    def test_fail_exit_one(self):
        self.assertEqual(gate.main(env=self.env(BUILD_RESULT="failure")), 1)

    def test_missing_inputs_fail(self):
        e = self.env()
        del e["BACKEND_TARGET"]
        self.assertEqual(gate.main(env=e), 1)

    def test_fallback_prints_warning_annotation(self):
        import contextlib
        import io
        buf = io.StringIO()
        with contextlib.redirect_stdout(buf):
            code = gate.main(env=self.env(BACKEND_TARGET="run", DETECT_MODE="FALLBACK", DETECT_REASON="git_error"))
        self.assertEqual(code, 0)
        self.assertIn("::warning", buf.getvalue())
        self.assertIn("git_error", buf.getvalue())


if __name__ == "__main__":
    unittest.main()
