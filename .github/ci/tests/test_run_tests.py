"""run_tests: 0건 발견·실패를 성공으로 처리하지 않는다."""

import contextlib
import io
import os
import shutil
import tempfile
import unittest

import run_tests


class RunTestsTest(unittest.TestCase):
    def setUp(self):
        self.top = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, self.top, True)
        # 실제 tests 패키지와 sys.modules 이름이 겹치지 않게 고유한 패키지 이름을 쓴다.
        self.tests = os.path.join(self.top, "suite_" + os.path.basename(self.top).replace("-", "_").lower())
        os.makedirs(self.tests)
        open(os.path.join(self.tests, "__init__.py"), "w").close()

    def add(self, name, body):
        with open(os.path.join(self.tests, name), "w", encoding="utf-8") as f:
            f.write("import unittest\n\nclass T(unittest.TestCase):\n" + body)

    def run_quiet(self):
        with contextlib.redirect_stderr(io.StringIO()), contextlib.redirect_stdout(io.StringIO()):
            return run_tests.run(self.tests, self.top)

    def test_zero_tests_is_failure(self):
        self.assertEqual(self.run_quiet(), 1)

    def test_failing_test_is_failure(self):
        self.add("test_x_fail.py", "    def test_a(self):\n        self.assertEqual(1, 2)\n")
        self.assertEqual(self.run_quiet(), 1)

    def test_import_error_is_failure(self):
        self.add("test_x_import.py", "    pass\nimport no_such_module_xyz\n")
        self.assertEqual(self.run_quiet(), 1)

    def test_passing_tests_succeed(self):
        self.add("test_x_ok.py", "    def test_a(self):\n        self.assertTrue(True)\n")
        self.assertEqual(self.run_quiet(), 0)


if __name__ == "__main__":
    unittest.main()
