"""gate 판정 테스트. BE·FE·E2E 감지 출력의 전체 조합을 곱집합으로 검사한다.

원본: docs/testing/fe02-ci-split.md, E2E 영역은 docs/testing/fe06-e2e-ci.md(Q2·Q3).
"""

import itertools
import os
import shutil
import tempfile
import unittest

import gate

# gate.py와 독립적으로 적은 허용 조합. (backend, frontend, e2e, mode, reason)
# 규칙: backend·frontend 중 하나라도 run이면 e2e도 run. e2e 전용 변경은 skip·skip·run.
VALID_COMBOS = {
    ("run", "run", "run", "NORMAL", "changed"),
    ("run", "skip", "run", "NORMAL", "changed"),
    ("skip", "run", "run", "NORMAL", "changed"),
    ("skip", "skip", "run", "NORMAL", "changed"),
    ("skip", "skip", "skip", "NORMAL", "docs_only"),
    ("run", "run", "run", "FALLBACK", "git_error"),
    ("run", "run", "run", "FALLBACK", "empty_diff"),
    ("run", "run", "run", "FALLBACK", "invalid_input"),
    ("run", "run", "run", "FALLBACK", "unknown_event"),
    ("run", "run", "run", "FULL", "push_main"),
    ("run", "run", "run", "FULL", "dispatch"),
}

AREAS = ["backend", "frontend", "e2e"]
CHANGES = ["success", "failure", "cancelled", "skipped", ""]
TARGETS = ["run", "skip", "", "RUN"]
MODES = ["NORMAL", "FALLBACK", "FULL", "", "normal", "SKIPPED"]
REASONS = ["changed", "docs_only", "git_error", "empty_diff", "invalid_input", "unknown_event",
           "push_main", "dispatch", "", "bogus"]
BUILDS = ["success", "failure", "cancelled", "skipped", ""]


def expected(area, changes, be, fe, e2e, mode, reason, build):
    """(passed, status) 참조 판정."""
    if changes != "success" or (be, fe, e2e, mode, reason) not in VALID_COMBOS:
        return False, "FAILED"
    target = {"backend": be, "frontend": fe, "e2e": e2e}[area]
    if target == "skip":
        return (True, "SKIPPED") if build == "skipped" else (False, "FAILED")
    return (True, mode) if build == "success" else (False, "FAILED")


class GateProductTest(unittest.TestCase):
    def test_full_cartesian_product(self):
        count = 0
        for args in itertools.product(AREAS, CHANGES, TARGETS, TARGETS, TARGETS, MODES, REASONS, BUILDS):
            v = gate.judge(*args)
            self.assertEqual((v.passed, v.status), expected(*args), args)
            count += 1
        self.assertEqual(count, 3 * 5 * 4 * 4 * 4 * 6 * 10 * 5)


class GateNamedCasesTest(unittest.TestCase):
    def j(self, area="frontend", changes="success", be="run", fe="run", e2e="run", mode="NORMAL",
          reason="changed", build="success"):
        return gate.judge(area, changes, be, fe, e2e, mode, reason, build)

    def test_contradiction_normal_changed_skip_skip_fails_on_both_gates(self):
        for area in AREAS:
            v = self.j(area=area, be="skip", fe="skip", e2e="skip", build="skipped")
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
        self.assertFalse(gate.judge("both", "success", "run", "run", "run", "NORMAL", "changed", "success").passed)

    def test_message_mentions_reason(self):
        v = self.j(mode="FALLBACK", reason="git_error")
        self.assertIn("git_error", v.message)

    # FE-06: e2e 영역(fe06-e2e-ci.md Q2·Q3)

    def test_e2e_only_change_skips_backend_and_frontend_gates(self):
        for area in ("backend", "frontend"):
            v = self.j(area=area, be="skip", fe="skip", e2e="run", build="skipped")
            self.assertEqual((v.passed, v.status), (True, "SKIPPED"), area)

    def test_e2e_only_change_runs_e2e_gate(self):
        v = self.j(area="e2e", be="skip", fe="skip", e2e="run", build="success")
        self.assertEqual((v.passed, v.status), (True, "NORMAL"))

    def test_e2e_must_run_when_backend_or_frontend_runs(self):
        for be, fe in (("run", "skip"), ("skip", "run"), ("run", "run")):
            for area in AREAS:
                v = self.j(area=area, be=be, fe=fe, e2e="skip", build="skipped")
                self.assertFalse(v.passed, (area, be, fe))

    def test_e2e_job_skipped_or_cancelled_when_run_fails(self):
        for build in ("skipped", "cancelled", "failure", ""):
            self.assertFalse(self.j(area="e2e", build=build).passed, build)

    def test_e2e_docs_only_is_skipped_pass(self):
        v = self.j(area="e2e", be="skip", fe="skip", e2e="skip", reason="docs_only", build="skipped")
        self.assertEqual((v.passed, v.status), (True, "SKIPPED"))

    def test_e2e_full_and_fallback_pass_only_on_success(self):
        for mode, reason in (("FULL", "push_main"), ("FULL", "dispatch"), ("FALLBACK", "git_error")):
            v = self.j(area="e2e", mode=mode, reason=reason)
            self.assertEqual((v.passed, v.status), (True, mode))
            self.assertFalse(self.j(area="e2e", mode=mode, reason=reason, build="failure").passed)


class GateMainTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, self.dir, True)
        self.summary = os.path.join(self.dir, "summary")

    def env(self, **kw):
        e = {"GATE_AREA": "frontend", "CHANGES_RESULT": "success", "BACKEND_TARGET": "skip",
             "FRONTEND_TARGET": "run", "E2E_TARGET": "run", "DETECT_MODE": "NORMAL", "DETECT_REASON": "changed",
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

    def test_missing_e2e_target_fails_every_gate(self):
        for area in AREAS:
            e = self.env(GATE_AREA=area, BACKEND_TARGET="run", FRONTEND_TARGET="run")
            del e["E2E_TARGET"]
            self.assertEqual(gate.main(env=e), 1, area)

    def test_e2e_gate_summary_title(self):
        self.assertEqual(gate.main(env=self.env(GATE_AREA="e2e")), 0)
        with open(self.summary, encoding="utf-8") as f:
            self.assertIn("Gate e2e-ci: NORMAL (PASS)", f.read())

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
