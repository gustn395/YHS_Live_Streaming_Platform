"""npm audit 판정 테스트(FE-D017·FE-D019). 원본: docs/frontend/decisions.md."""

import json
import os
import shutil
import tempfile
import unittest

import npm_audit_report as nar


def report(info=0, low=0, moderate=0, high=0, critical=0, total=None, version=2, extra=None):
    counts = {"info": info, "low": low, "moderate": moderate, "high": high, "critical": critical,
              "total": info + low + moderate + high + critical if total is None else total}
    d = {"auditReportVersion": version, "vulnerabilities": {}, "metadata": {"vulnerabilities": counts}}
    if extra:
        d.update(extra)
    return json.dumps(d).encode()


class BlockingTest(unittest.TestCase):
    def judge(self, exit_code, raw):
        return nar.judge(exit_code, raw, mode="blocking")

    def test_clean_is_pass(self):
        self.assertEqual(self.judge(0, report()).verdict, "PASS")

    def test_moderate_only_below_level_is_pass(self):
        v = self.judge(0, report(moderate=9))
        self.assertEqual(v.verdict, "PASS")
        self.assertEqual(v.counts["moderate"], 9)

    def test_high_is_vuln(self):
        self.assertEqual(self.judge(1, report(high=1)).verdict, "AUDIT_VULN")

    def test_critical_is_vuln(self):
        self.assertEqual(self.judge(1, report(critical=2)).verdict, "AUDIT_VULN")

    def test_error_key_is_error(self):
        raw = json.dumps({"error": {"code": "ENOAUDIT", "summary": "x"}}).encode()
        self.assertEqual(self.judge(1, raw).verdict, "AUDIT_ERROR")

    def test_error_key_with_counts_is_still_error(self):
        self.assertEqual(self.judge(1, report(extra={"error": {"code": "E"}})).verdict, "AUDIT_ERROR")

    def test_invalid_json_is_error(self):
        self.assertEqual(self.judge(1, b"npm ERR! network").verdict, "AUDIT_ERROR")

    def test_empty_output_is_error(self):
        self.assertEqual(self.judge(0, b"").verdict, "AUDIT_ERROR")

    def test_non_object_json_is_error(self):
        self.assertEqual(self.judge(0, b"[]").verdict, "AUDIT_ERROR")

    def test_missing_exit_code_is_error(self):
        self.assertEqual(self.judge(None, report()).verdict, "AUDIT_ERROR")

    def test_missing_version_is_error(self):
        d = json.loads(report())
        del d["auditReportVersion"]
        self.assertEqual(self.judge(0, json.dumps(d).encode()).verdict, "AUDIT_ERROR")

    def test_unexpected_version_is_error(self):
        self.assertEqual(self.judge(0, report(version=1)).verdict, "AUDIT_ERROR")

    def test_missing_metadata_is_error(self):
        self.assertEqual(self.judge(0, json.dumps({"auditReportVersion": 2}).encode()).verdict, "AUDIT_ERROR")

    def test_missing_severity_field_is_error(self):
        for key in ("info", "low", "moderate", "high", "critical", "total"):
            d = json.loads(report())
            del d["metadata"]["vulnerabilities"][key]
            self.assertEqual(self.judge(0, json.dumps(d).encode()).verdict, "AUDIT_ERROR", key)

    def test_wrong_value_types_are_error(self):
        for bad in (-1, "0", 1.5, True, None):
            d = json.loads(report())
            d["metadata"]["vulnerabilities"]["high"] = bad
            self.assertEqual(self.judge(0, json.dumps(d).encode()).verdict, "AUDIT_ERROR", repr(bad))

    def test_total_mismatch_is_error(self):
        self.assertEqual(self.judge(0, report(low=1, total=0)).verdict, "AUDIT_ERROR")

    def test_nonzero_exit_without_high_is_error(self):
        self.assertEqual(self.judge(1, report(moderate=3)).verdict, "AUDIT_ERROR")

    def test_zero_exit_with_high_is_error(self):
        self.assertEqual(self.judge(0, report(high=1)).verdict, "AUDIT_ERROR")


class ReportModeTest(unittest.TestCase):
    def judge(self, exit_code, raw):
        return nar.judge(exit_code, raw, mode="report")

    def test_vulns_reported_as_found(self):
        v = self.judge(1, report(moderate=9))
        self.assertEqual(v.verdict, "FOUND")
        self.assertEqual(v.counts["total"], 9)

    def test_clean(self):
        self.assertEqual(self.judge(0, report()).verdict, "PASS")

    def test_error_is_error_not_zero(self):
        v = self.judge(1, b"garbage")
        self.assertEqual(v.verdict, "AUDIT_ERROR")
        self.assertIsNone(v.counts)

    def test_nonzero_exit_with_zero_total_is_error(self):
        self.assertEqual(self.judge(1, report()).verdict, "AUDIT_ERROR")


class MainTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, self.dir, True)
        self.summary = os.path.join(self.dir, "summary")

    def files(self, raw, code):
        j = os.path.join(self.dir, "a.json")
        e = os.path.join(self.dir, "a.exit")
        with open(j, "wb") as f:
            f.write(raw)
        if code is not None:
            with open(e, "w", encoding="utf-8") as f:
                f.write(f"{code}\n")
        return j, e

    def run_main(self, mode, raw, code):
        j, e = self.files(raw, code)
        return nar.main(["--mode", mode, "--json-file", j, "--exit-code-file", e],
                        env={"GITHUB_STEP_SUMMARY": self.summary})

    def summary_text(self):
        with open(self.summary, encoding="utf-8") as f:
            return f.read()

    def test_blocking_pass_exit_zero(self):
        self.assertEqual(self.run_main("blocking", report(moderate=9), 0), 0)
        self.assertIn("PASS", self.summary_text())
        self.assertIn("--omit=dev", self.summary_text())

    def test_blocking_vuln_exit_one(self):
        self.assertEqual(self.run_main("blocking", report(high=1), 1), 1)
        self.assertIn("AUDIT_VULN", self.summary_text())

    def test_blocking_error_exit_one_and_not_zero_count(self):
        self.assertEqual(self.run_main("blocking", b"", 1), 1)
        text = self.summary_text()
        self.assertIn("AUDIT_ERROR", text)
        self.assertNotIn("| 0 |", text)

    def test_blocking_missing_exit_file_is_error(self):
        self.assertEqual(self.run_main("blocking", report(), None), 1)

    def test_blocking_garbage_exit_file_is_error(self):
        j, e = self.files(report(), "x")
        self.assertEqual(nar.main(["--mode", "blocking", "--json-file", j, "--exit-code-file", e],
                                  env={"GITHUB_STEP_SUMMARY": self.summary}), 1)

    def test_report_mode_never_fails(self):
        self.assertEqual(self.run_main("report", b"garbage", 1), 0)
        self.assertIn("AUDIT_ERROR", self.summary_text())
        self.assertEqual(self.run_main("report", report(moderate=9), 1), 0)


if __name__ == "__main__":
    unittest.main()
