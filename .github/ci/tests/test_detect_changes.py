"""detect_changes 단위(L1)·임시 Git 저장소(L2) 테스트. 원본: docs/testing/fe02-ci-split.md."""

import os
import shutil
import subprocess
import tempfile
import unittest

import detect_changes as dc

# Gate와 독립적으로 적은 허용 조합(fe02-ci-split.md 2장). (backend, frontend, mode, reason)
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

MISSING_SHA = "deadbeef" * 5  # 형식은 맞지만 저장소에 없는 40자리 SHA


def combo(result):
    return (result.backend, result.frontend, result.mode, result.reason)


class ClassifyTest(unittest.TestCase):
    def assertClass(self, path, expected):
        self.assertEqual(dc.classify(path), expected, path)

    def test_backend_workflow_is_backend_not_common(self):
        self.assertClass(".github/workflows/backend-ci.yml", "backend")

    def test_frontend_workflow_is_frontend_not_common(self):
        self.assertClass(".github/workflows/frontend-ci.yml", "frontend")

    def test_other_github_paths_are_both(self):
        for p in (".github/ci/detect_changes.py", ".github/actions/ci-gate/action.yml",
                  ".github/workflows/other.yml", ".github/ci/tests/test_gate.py"):
            self.assertClass(p, "both")

    def test_backend_tree_is_backend(self):
        for p in ("backend/app/build.gradle.kts", "backend/README.md", "backend/modules/member/AGENTS.md",
                  "backend/.gitignore"):
            self.assertClass(p, "backend")

    def test_frontend_tree_is_frontend(self):
        for p in ("frontend/src/main.tsx", "frontend/AGENTS.md", "frontend/.gitignore", "frontend/package-lock.json"):
            self.assertClass(p, "frontend")

    def test_docs_api_is_both(self):
        self.assertClass("docs/api/openapi.yaml", "none")  # L3-2 임시 실패 주입 — 원복 예정
        self.assertClass("docs/api/.gitkeep", "both")

    def test_other_docs_are_none(self):
        for p in ("docs/INDEX.md", "docs/testing/fe02-ci-split.md", "docs/frontend/decisions.md", "docs/apiary.md"):
            self.assertClass(p, "none")

    def test_root_markdown_is_none(self):
        for p in ("README.md", "AGENTS.md", "CLAUDE.md"):
            self.assertClass(p, "none")

    def test_root_shared_config_is_both(self):
        for p in (".gitignore", ".env.example", ".coderabbit.yaml"):
            self.assertClass(p, "both")

    def test_unclassified_is_both(self):
        for p in ("tools/ci-verify.txt", "README.MD", "notes/a.md", "backend", "frontendx/a.ts", "Makefile"):
            self.assertClass(p, "both")

    def test_unusual_characters_do_not_change_rules(self):
        self.assertClass("frontend/src/공백 있는 파일.ts", "frontend")
        self.assertClass("backend/a\nb.txt", "backend")
        self.assertClass("docs/줄\n바꿈.md", "none")


class SplitZTest(unittest.TestCase):
    def test_splits_on_nul_and_drops_trailing_empty(self):
        self.assertEqual(dc.split_z(b"a\x00b c\x00"), ["a", "b c"])

    def test_keeps_newlines_inside_names(self):
        self.assertEqual(dc.split_z(b"x\ny\x00"), ["x\ny"])

    def test_utf8_names(self):
        self.assertEqual(dc.split_z("한글.md\x00".encode()), ["한글.md"])

    def test_empty_input(self):
        self.assertEqual(dc.split_z(b""), [])

    def test_invalid_utf8_is_preserved_not_raised(self):
        (name,) = dc.split_z(b"bad\xff.txt\x00")
        self.assertTrue(name.startswith("bad"))


class FakeGit:
    def __init__(self, responses):
        self.responses = list(responses)
        self.calls = []

    def __call__(self, args):
        self.calls.append(args)
        return self.responses.pop(0)


BASE = "a" * 40
HEAD = "b" * 40
MB = "c" * 40


class DecideUnitTest(unittest.TestCase):
    def pr(self, git, base=BASE, head=HEAD):
        return dc.decide("pull_request", base, head, run_git=git)

    def test_push_is_full_without_git(self):
        git = FakeGit([])
        r = dc.decide("push", "", "", run_git=git)
        self.assertEqual(combo(r), ("run", "run", "FULL", "push_main"))
        self.assertEqual(git.calls, [])

    def test_dispatch_is_full(self):
        r = dc.decide("workflow_dispatch", "", "", run_git=FakeGit([]))
        self.assertEqual(combo(r), ("run", "run", "FULL", "dispatch"))

    def test_unknown_event_is_fallback(self):
        r = dc.decide("pull_request_target", BASE, HEAD, run_git=FakeGit([]))
        self.assertEqual(combo(r), ("run", "run", "FALLBACK", "unknown_event"))

    def test_invalid_sha_format_is_fallback_without_git(self):
        for base, head in (("", HEAD), (BASE, ""), ("A" * 40, HEAD), (BASE[:39], HEAD),
                           ("$(id)" + "a" * 35, HEAD), (BASE, HEAD + "\n")):
            git = FakeGit([])
            r = self.pr(git, base, head)
            self.assertEqual(combo(r), ("run", "run", "FALLBACK", "invalid_input"), (base, head))
            self.assertEqual(git.calls, [])

    def test_merge_base_failure_is_git_error(self):
        r = self.pr(FakeGit([(128, b"", b"fatal: Not a valid commit name")]))
        self.assertEqual(combo(r), ("run", "run", "FALLBACK", "git_error"))

    def test_merge_base_garbage_output_is_git_error(self):
        r = self.pr(FakeGit([(0, b"not-a-sha\n", b"")]))
        self.assertEqual(combo(r), ("run", "run", "FALLBACK", "git_error"))

    def test_diff_failure_is_git_error(self):
        r = self.pr(FakeGit([(0, MB.encode() + b"\n", b""), (128, b"", b"fatal: bad object")]))
        self.assertEqual(combo(r), ("run", "run", "FALLBACK", "git_error"))

    def test_git_not_executable_is_git_error(self):
        def boom(args):
            raise OSError("git missing")
        r = self.pr(boom)
        self.assertEqual(combo(r), ("run", "run", "FALLBACK", "git_error"))

    def test_empty_diff_is_fallback(self):
        r = self.pr(FakeGit([(0, MB.encode() + b"\n", b""), (0, b"", b"")]))
        self.assertEqual(combo(r), ("run", "run", "FALLBACK", "empty_diff"))

    def test_diff_uses_merge_base_no_renames_and_z(self):
        git = FakeGit([(0, MB.encode() + b"\n", b""), (0, b"backend/a\x00", b"")])
        self.pr(git)
        self.assertEqual(git.calls[0], ["merge-base", BASE, HEAD])
        self.assertEqual(git.calls[1], ["diff", "--name-only", "--no-renames", "-z", MB, HEAD, "--"])

    def diff(self, raw):
        return self.pr(FakeGit([(0, MB.encode() + b"\n", b""), (0, raw, b"")]))

    def test_backend_only(self):
        self.assertEqual(combo(self.diff(b"backend/a.java\x00docs/x.md\x00")), ("run", "skip", "NORMAL", "changed"))

    def test_frontend_only(self):
        self.assertEqual(combo(self.diff(b"frontend/a.ts\x00")), ("skip", "run", "NORMAL", "changed"))

    def test_both(self):
        self.assertEqual(combo(self.diff(b"backend/a\x00frontend/b\x00")), ("run", "run", "NORMAL", "changed"))

    def test_common_runs_both(self):
        self.assertEqual(combo(self.diff(b".github/ci/gate.py\x00")), ("run", "run", "NORMAL", "changed"))

    def test_docs_only_skips_both(self):
        self.assertEqual(combo(self.diff(b"docs/a.md\x00README.md\x00")), ("skip", "skip", "NORMAL", "docs_only"))

    def test_classified_paths_are_returned(self):
        r = self.diff(b"backend/a\x00docs/b.md\x00")
        self.assertEqual(r.classified, [("backend/a", "backend"), ("docs/b.md", "none")])

    def test_every_result_is_a_valid_combination(self):
        cases = [dc.decide("push", "", "", run_git=FakeGit([])),
                 dc.decide("workflow_dispatch", "", "", run_git=FakeGit([])),
                 dc.decide("x", "", "", run_git=FakeGit([])),
                 self.pr(FakeGit([]), "", ""),
                 self.pr(FakeGit([(1, b"", b"")])),
                 self.diff(b""), self.diff(b"backend/a\x00"), self.diff(b"frontend/a\x00"),
                 self.diff(b"x\x00"), self.diff(b"docs/a.md\x00")]
        for r in cases:
            self.assertIn(combo(r), VALID_COMBOS)


class WriteOutputsTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, self.dir, True)
        self.out = os.path.join(self.dir, "out")
        self.summary = os.path.join(self.dir, "summary")

    def run_main(self, env):
        base = {"GITHUB_OUTPUT": self.out, "GITHUB_STEP_SUMMARY": self.summary}
        base.update(env)
        return dc.main(env=base, run_git=FakeGit([(0, MB.encode() + b"\n", b""),
                                                   (0, b"::warning::x\x00backend/a\x00", b"")]))

    def test_outputs_only_fixed_tokens(self):
        code = self.run_main({"EVENT_NAME": "pull_request", "BASE_SHA": BASE, "HEAD_SHA": HEAD})
        self.assertEqual(code, 0)
        with open(self.out, encoding="utf-8") as f:
            lines = f.read().splitlines()
        self.assertEqual(lines, ["backend=run", "frontend=run", "mode=NORMAL", "reason=changed"])

    def test_summary_has_no_raw_paths(self):
        self.run_main({"EVENT_NAME": "pull_request", "BASE_SHA": BASE, "HEAD_SHA": HEAD})
        with open(self.summary, encoding="utf-8") as f:
            text = f.read()
        self.assertNotIn("::warning::", text)
        self.assertNotIn("backend/a", text)
        self.assertIn("NORMAL", text)

    def test_missing_output_file_env_fails(self):
        code = dc.main(env={"EVENT_NAME": "push"}, run_git=FakeGit([]))
        self.assertNotEqual(code, 0)


class TempRepoTest(unittest.TestCase):
    """L2: 실제 git으로 merge-base·diff를 실행한다. git이 없으면 건너뛰지 않고 실패한다."""

    def setUp(self):
        self.repo = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, self.repo, True)
        self.git("init", "-q", "-b", "main")
        self.git("config", "user.email", "ci@example.invalid")
        self.git("config", "user.name", "ci")
        self.git("config", "core.autocrlf", "false")
        self.write("backend/a.txt", "a")
        self.write("frontend/b.txt", "b")
        self.write("docs/c.md", "c")
        self.base = self.commit("base")

    def git(self, *args):
        return subprocess.run(["git", *args], cwd=self.repo, check=True, capture_output=True).stdout.decode().strip()

    def write(self, path, text):
        full = os.path.join(self.repo, *path.split("/"))
        os.makedirs(os.path.dirname(full), exist_ok=True)
        with open(full, "w", encoding="utf-8") as f:
            f.write(text)

    def commit(self, msg):
        self.git("add", "-A")
        self.git("commit", "-q", "--allow-empty", "-m", msg)
        return self.git("rev-parse", "HEAD")

    def branch(self):
        self.git("switch", "-q", "-c", "feature", self.base)

    def decide(self, base=None, head=None):
        return dc.decide("pull_request", base or self.base, head or self.git("rev-parse", "HEAD"),
                         run_git=dc.git_runner(self.repo))

    def test_backend_only(self):
        self.branch()
        self.write("backend/a.txt", "a2")
        self.commit("be")
        self.assertEqual(combo(self.decide()), ("run", "skip", "NORMAL", "changed"))

    def test_frontend_only(self):
        self.branch()
        self.write("frontend/b.txt", "b2")
        self.commit("fe")
        self.assertEqual(combo(self.decide()), ("skip", "run", "NORMAL", "changed"))

    def test_docs_only(self):
        self.branch()
        self.write("docs/c.md", "c2")
        self.write("README.md", "r")
        self.commit("docs")
        self.assertEqual(combo(self.decide()), ("skip", "skip", "NORMAL", "docs_only"))

    def test_multiple_commits_accumulate(self):
        self.branch()
        self.write("backend/a.txt", "a2")
        self.commit("be")
        self.write("frontend/b.txt", "b2")
        self.commit("fe")
        self.assertEqual(combo(self.decide()), ("run", "run", "NORMAL", "changed"))

    def test_change_then_revert_across_commits_is_empty_diff(self):
        self.branch()
        self.write("backend/a.txt", "a2")
        self.commit("be")
        self.write("backend/a.txt", "a")
        self.commit("revert")
        self.assertEqual(combo(self.decide()), ("run", "run", "FALLBACK", "empty_diff"))

    def test_delete(self):
        self.branch()
        self.git("rm", "-q", "frontend/b.txt")
        self.commit("del")
        r = self.decide()
        self.assertEqual(combo(r), ("skip", "run", "NORMAL", "changed"))
        self.assertIn(("frontend/b.txt", "frontend"), r.classified)

    def test_move_across_boundary_counts_both_sides(self):
        self.branch()
        self.git("mv", "backend/a.txt", "frontend/a.txt")
        self.commit("mv")
        r = self.decide()
        self.assertEqual(combo(r), ("run", "run", "NORMAL", "changed"))
        self.assertIn(("backend/a.txt", "backend"), r.classified)
        self.assertIn(("frontend/a.txt", "frontend"), r.classified)

    def test_move_inside_frontend(self):
        self.branch()
        self.git("mv", "frontend/b.txt", "frontend/c.txt")
        self.commit("mv")
        self.assertEqual(combo(self.decide()), ("skip", "run", "NORMAL", "changed"))

    def test_unusual_file_names(self):
        self.branch()
        self.write("frontend/공백 이름.ts", "x")
        self.commit("u")
        r = self.decide()
        self.assertIn(("frontend/공백 이름.ts", "frontend"), r.classified)

    def test_empty_commit_is_empty_diff(self):
        self.branch()
        self.commit("empty")
        self.assertEqual(combo(self.decide()), ("run", "run", "FALLBACK", "empty_diff"))

    def test_missing_valid_sha_is_real_git_error(self):
        self.branch()
        self.write("backend/a.txt", "a2")
        self.commit("be")
        self.assertEqual(combo(self.decide(base=MISSING_SHA)), ("run", "run", "FALLBACK", "git_error"))

    def test_unrelated_histories_merge_base_fails(self):
        # switch --orphan은 index·작업 트리를 비운다(checkout --orphan과 다름).
        self.git("switch", "-q", "--orphan", "other")
        self.write("frontend/z.txt", "z")
        self.git("add", "frontend/z.txt")
        self.git("commit", "-q", "-m", "orphan")
        self.assertEqual(combo(self.decide()), ("run", "run", "FALLBACK", "git_error"))

    def test_base_advanced_after_branch_ignores_base_only_changes(self):
        self.branch()
        self.write("frontend/b.txt", "b2")
        head = self.commit("fe")
        self.git("switch", "-q", "main")
        self.write("backend/a.txt", "main-change")
        new_base = self.commit("main moves")
        r = self.decide(base=new_base, head=head)
        self.assertEqual(combo(r), ("skip", "run", "NORMAL", "changed"))


if __name__ == "__main__":
    unittest.main()
