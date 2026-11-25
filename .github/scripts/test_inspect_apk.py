import unittest

from inspect_apk import ALLOWED_EXPORTED, NAMESPACE, manifest_problems

HEAD = """N: android=http://schemas.android.com/apk/res/android (line=2)
  E: manifest (line=2)
    A: package="io.github.dumahuch.pogodaapp" (Raw: "io.github.dumahuch.pogodaapp")
      E: uses-permission (line=12)
        A: http://schemas.android.com/apk/res/android:name(0x01010003)="android.permission.INTERNET" (Raw: "x")
      E: application (line=22)
        A: http://schemas.android.com/apk/res/android:allowBackup(0x01010280)=%s
%s"""

MAIN = """          E: activity (line=36)
            A: http://schemas.android.com/apk/res/android:name(0x01010003)=".MainActivity" (Raw: ".MainActivity")
            A: http://schemas.android.com/apk/res/android:exported(0x01010010)=true
            E: intent-filter (line=40)
              E: action (line=41)
                A: http://schemas.android.com/apk/res/android:name(0x01010003)="android.intent.action.MAIN" (Raw: "android.intent.action.MAIN")
"""
CONFIG = """          E: activity (line=59)
            A: http://schemas.android.com/apk/res/android:name(0x01010003)="io.github.dumahuch.pogodaapp.widget.WidgetConfigActivity" (Raw: "x")
            A: http://schemas.android.com/apk/res/android:exported(0x01010010)=true
"""
CLOSED = """          E: service (line=68)
            A: http://schemas.android.com/apk/res/android:name(0x01010003)=".notify.WeatherJobService" (Raw: ".notify.WeatherJobService")
            A: http://schemas.android.com/apk/res/android:exported(0x01010010)=false
"""
LEAK_SERVICE = """          E: service (line=90)
            A: http://schemas.android.com/apk/res/android:name(0x01010003)="com.example.Leaky" (Raw: "com.example.Leaky")
            A: http://schemas.android.com/apk/res/android:exported(0x01010010)=true
"""


def tree(backup="false", body=MAIN + CONFIG + CLOSED, extra_app=""):
    text = HEAD % (backup, body)
    if extra_app:
        text = text.replace("          E: activity (line=36)", extra_app + "          E: activity (line=36)", 1)
    return text


class ManifestProblemsTest(unittest.TestCase):
    def test_expected_manifest_is_clean(self):
        problems, exported = manifest_problems(tree(), ALLOWED_EXPORTED)
        self.assertEqual(problems, [])
        self.assertEqual(exported, [f"{NAMESPACE}.MainActivity", f"{NAMESPACE}.widget.WidgetConfigActivity"])
        self.assertTrue(set(exported) <= ALLOWED_EXPORTED)

    def test_relative_names_get_the_namespace(self):
        self.assertIn(f"{NAMESPACE}.MainActivity", manifest_problems(tree(), set())[1])

    def test_unexpected_exported_component_fails(self):
        problems, _ = manifest_problems(tree(body=MAIN + CONFIG + CLOSED + LEAK_SERVICE), ALLOWED_EXPORTED)
        self.assertEqual(len(problems), 1)
        self.assertIn("com.example.Leaky", problems[0])

    def test_closed_component_is_fine(self):
        self.assertEqual(manifest_problems(tree(body=MAIN + CONFIG + CLOSED), ALLOWED_EXPORTED)[0], [])

    def test_backup_must_be_false(self):
        problems, _ = manifest_problems(tree(backup="true"), ALLOWED_EXPORTED)
        self.assertTrue(any("allowBackup" in p for p in problems))

    def test_missing_backup_is_a_problem_because_default_is_true(self):
        text = tree().replace("        A: http://schemas.android.com/apk/res/android:allowBackup(0x01010280)=false\n", "")
        self.assertTrue(any("allowBackup" in p for p in manifest_problems(text, ALLOWED_EXPORTED)[0]))

    def test_debuggable_fails(self):
        text = tree().replace("allowBackup(0x01010280)=false\n", "allowBackup(0x01010280)=false\n        A: http://schemas.android.com/apk/res/android:debuggable(0x0101000f)=true\n", 1)
        self.assertTrue(any("debuggable" in p for p in manifest_problems(text, ALLOWED_EXPORTED)[0]))


if __name__ == "__main__":
    unittest.main()
