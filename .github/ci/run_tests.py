"""CI 스크립트 unittest 실행기. 0건 발견·실패·import 오류를 성공으로 처리하지 않는다.

사용: python3 -B -I .github/ci/run_tests.py
"""

import os
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))


def run(start_dir, top_dir):
    suite = unittest.defaultTestLoader.discover(start_dir, top_level_dir=top_dir)
    # buffer=True: 테스트가 찍는 `::error` 같은 줄이 CI 로그에서 워크플로 명령으로 해석되지 않게 가둔다.
    result = unittest.TextTestRunner(verbosity=1, buffer=True).run(suite)
    if result.testsRun == 0:
        print("run_tests: 발견된 테스트가 0건이다 — 실패로 처리한다", file=sys.stderr)
        return 1
    return 0 if result.wasSuccessful() else 1


if __name__ == "__main__":
    sys.exit(run(os.path.join(HERE, "tests"), HERE))
