import json
import tempfile
import unittest
import zipfile
from pathlib import Path

import verify_numbers as v

REPO = Path(__file__).resolve().parents[2]


class ArithmeticTest(unittest.TestCase):
    def test_product(self):
        self.assertEqual(v.arithmetic("4 * 1024 * 1024"), 4194304)

    def test_suffixes_and_underscores(self):
        self.assertEqual(v.arithmetic("10 * 60_000L"), 600000)
        self.assertEqual(v.arithmetic("1L * 1024 * 1024 // комментарий"), 1048576)

    def test_not_a_number(self):
        self.assertIsNone(v.arithmetic("listOf(1)"))
        self.assertIsNone(v.arithmetic("__import__('os')"))


class SpecsTest(unittest.TestCase):
    def test_same_placeholders_in_different_order(self):
        self.assertEqual(v.specs("%1$s из %2$d"), v.specs("%2$d от %1$s"))

    def test_lost_placeholder_differs(self):
        self.assertNotEqual(v.specs("%s и %d"), v.specs("%s"))

    def test_percent_sign_is_not_a_placeholder(self):
        self.assertEqual(v.specs("100%% влажность"), [])

    def test_plurals_union(self):
        self.assertEqual(v.specs({"one": "%d день", "other": "%d дня"}), ["d"])


class WorkflowTest(unittest.TestCase):
    def test_allow_lists_per_call(self):
        text = (
            "python3 inspect_apk.py a.apk --allow-permission A --allow-permission B\n"
            "python3 inspect_apk.py b.apk --allow-permission A\n"
        )
        self.assertEqual(v.allow_lists(text), [{"A", "B"}, {"A"}])


class ReportTest(unittest.TestCase):
    def test_counts_and_table(self):
        report = v.Report()
        report.compare("группа", "равно", 1, 1)
        report.compare("группа", "не равно", 1, 2)
        self.assertEqual((report.count(v.OK), report.count(v.FAIL)), (1, 1))
        self.assertIn("| не равно | 1 | 2 | ❌ FAIL |", report.markdown())

    def test_pipe_in_cell_is_escaped(self):
        report = v.Report()
        report.add("g", "a|b", "x", "y", v.INFO)
        self.assertIn("a\\|b", report.markdown())


class JunitApkTest(unittest.TestCase):
    def make_root(self, directory: Path, methods: int) -> Path:
        tests = directory / "app" / "src" / "test"
        tests.mkdir(parents=True)
        (tests / "AaaTest.kt").write_text("class AaaTest {\n" + "    @Test\n    fun t() {}\n" * methods + "}\n", encoding="utf-8")
        return directory

    def results(self, directory: Path, tests: int, failures: int = 0, skipped: int = 0) -> Path:
        out = directory / "results"
        out.mkdir()
        (out / "TEST-a.xml").write_text(
            f'<testsuite name="a" tests="{tests}" failures="{failures}" errors="0" skipped="{skipped}"></testsuite>', encoding="utf-8")
        return out

    def test_junit_ok(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self.make_root(Path(tmp), 3)
            report = v.Report()
            v.check_junit(root, self.results(Path(tmp), 3), report)
            self.assertEqual(report.count(v.FAIL), 0)

    def test_junit_fewer_tests_than_declared(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self.make_root(Path(tmp), 3)
            report = v.Report()
            v.check_junit(root, self.results(Path(tmp), 2), report)
            self.assertEqual(report.count(v.FAIL), 1)

    def test_junit_failure(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self.make_root(Path(tmp), 3)
            report = v.Report()
            v.check_junit(root, self.results(Path(tmp), 3, failures=1), report)
            self.assertEqual(report.count(v.FAIL), 1)

    def test_junit_expected_skip_is_not_a_warning(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self.make_root(Path(tmp), 3)
            report = v.Report()
            v.check_junit(root, self.results(Path(tmp), 3, skipped=1), report, {"tests_skipped_expected": 1})
            self.assertEqual(report.count(v.WARN), 0)

    def test_junit_unexpected_skip_is_a_warning(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self.make_root(Path(tmp), 3)
            report = v.Report()
            v.check_junit(root, self.results(Path(tmp), 3, skipped=2), report, {"tests_skipped_expected": 1})
            self.assertEqual(report.count(v.WARN), 1)

    def test_junit_skipped_without_directory(self):
        report = v.Report()
        v.check_junit(Path("."), None, report)
        self.assertEqual(report.count(v.SKIP), 1)

    def apk(self, directory: Path, abis: list[str]) -> Path:
        path = directory / "a.apk"
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr("classes.dex", b"dex")
            archive.writestr("classes2.dex", b"dex")
            for abi in abis:
                archive.writestr(f"lib/{abi}/libmaplibre.so", b"so")
        return path

    def test_apk_abis_match(self):
        claims = {"apk": {"release": {"abis": ["arm64-v8a"], "size_mb": None}}}
        with tempfile.TemporaryDirectory() as tmp:
            report = v.Report()
            v.check_apk(claims, self.apk(Path(tmp), ["arm64-v8a"]), "release", report)
            self.assertEqual(report.count(v.FAIL), 0)

    def test_apk_extra_abi_fails(self):
        claims = {"apk": {"release": {"abis": ["arm64-v8a"], "size_mb": None}}}
        with tempfile.TemporaryDirectory() as tmp:
            report = v.Report()
            v.check_apk(claims, self.apk(Path(tmp), ["arm64-v8a", "x86_64"]), "release", report)
            self.assertEqual(report.count(v.FAIL), 1)

    def test_apk_size_out_of_range_fails(self):
        claims = {"apk": {"debug": {"abis": ["arm64-v8a"], "size_mb": {"min": 10, "max": 20}}}}
        with tempfile.TemporaryDirectory() as tmp:
            report = v.Report()
            v.check_apk(claims, self.apk(Path(tmp), ["arm64-v8a"]), "debug", report)
            self.assertEqual(report.count(v.FAIL), 1)


class ClaimsDriftTest(unittest.TestCase):
    """Заявленное расходится с кодом: сверка должна упасть, а не промолчать."""

    def run_with(self, change):
        claims = json.loads((REPO / "release" / "expected-numbers.json").read_text(encoding="utf-8"))
        change(claims)
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "claims.json"
            path.write_text(json.dumps(claims), encoding="utf-8")
            return v.run(REPO, path, None, None, "debug")

    def test_new_permission_is_caught(self):
        report = self.run_with(lambda c: c["permissions"].append("android.permission.CAMERA"))
        self.assertGreater(report.count(v.FAIL), 0)

    def test_new_host_is_caught(self):
        report = self.run_with(lambda c: c["hosts"].remove("api.brightsky.dev"))
        self.assertGreater(report.count(v.FAIL), 0)

    def test_changed_limit_is_caught(self):
        report = self.run_with(lambda c: c["limits"].update(http_max_body_bytes=1))
        self.assertGreater(report.count(v.FAIL), 0)

    def test_lost_tests_are_caught(self):
        report = self.run_with(lambda c: c.update(tests_min=10_000))
        self.assertGreater(report.count(v.FAIL), 0)


class RepositoryTest(unittest.TestCase):
    def test_repository_matches_claims(self):
        report = v.run(REPO, REPO / "release" / "expected-numbers.json", None, None, "debug")
        failed = [f"{r.group}: {r.what}: заявлено {r.claimed}, измерено {r.measured}" for r in report.rows if r.status == v.FAIL]
        self.assertEqual(failed, [])


if __name__ == "__main__":
    unittest.main()
