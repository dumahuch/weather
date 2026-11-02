import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("code_hygiene.py")

MANIFEST = '<manifest><application><activity android:name=".MainActivity"/></application></manifest>'


def make_tree(root: Path, code: str, tests: str = "", strings: str = "") -> None:
    (root / "app/src/main/kotlin").mkdir(parents=True)
    (root / "app/src/test/kotlin").mkdir(parents=True)
    (root / "app/src/main/res/values").mkdir(parents=True)
    (root / "app/src/main/AndroidManifest.xml").write_text(MANIFEST, encoding="utf-8")
    (root / "app/src/main/kotlin/A.kt").write_text(code, encoding="utf-8")
    (root / "app/src/test/kotlin/ATest.kt").write_text(tests, encoding="utf-8")
    (root / "app/src/main/res/values/strings.xml").write_text(f"<resources>{strings}</resources>", encoding="utf-8")


def run(root: Path) -> tuple[int, str]:
    result = subprocess.run([sys.executable, str(SCRIPT), str(root)], capture_output=True, text=True)
    return result.returncode, result.stdout


class HygieneTest(unittest.TestCase):
    def check(self, **kwargs):
        with tempfile.TemporaryDirectory() as tmp:
            make_tree(Path(tmp), **kwargs)
            return run(Path(tmp))

    def test_clean_code_passes(self):
        code, _ = self.check(code="internal fun used() = 1\nfun caller() = used()\n", tests="val y = caller()\n")
        self.assertEqual(code, 0)

    def test_unused_function_fails(self):
        code, out = self.check(code="internal fun orphan() = 1\n")
        self.assertEqual(code, 1)
        self.assertIn("orphan", out)

    def test_used_only_in_tests_is_a_warning(self):
        code, out = self.check(code="internal fun forTest() = 1\n", tests="val x = forTest()\n")
        self.assertEqual(code, 0)
        self.assertIn("только в тестах", out)

    def test_unused_string_fails(self):
        code, out = self.check(code="fun a() = R.string.used\n", strings='<string name="used">a</string><string name="dead">b</string>')
        self.assertEqual(code, 1)
        self.assertIn("dead", out)

    def test_too_long_file_fails(self):
        code, out = self.check(code="fun a() = 1\n" + "// x\n" * 701)
        self.assertEqual(code, 1)
        self.assertIn("строк", out)


if __name__ == "__main__":
    unittest.main()
