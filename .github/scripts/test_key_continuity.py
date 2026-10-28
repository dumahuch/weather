import unittest

from key_continuity import MARKER, fingerprint_from_notes, verdict

A = "a" * 64
B = "b" * 64


def notes(fingerprint: str) -> str:
    return f"Шаг 0: пустое приложение.\n\n{MARKER}: `{fingerprint}`\n"


class FingerprintTest(unittest.TestCase):
    def test_finds_fingerprint(self):
        self.assertEqual(fingerprint_from_notes(notes(A)), A)

    def test_ignores_other_hashes(self):
        self.assertIsNone(fingerprint_from_notes(f"контрольная сумма {A}"))

    def test_empty_notes(self):
        self.assertIsNone(fingerprint_from_notes(""))

    def test_rejects_wrong_length(self):
        self.assertIsNone(fingerprint_from_notes(f"{MARKER}: {'a' * 63}"))


class VerdictTest(unittest.TestCase):
    def test_first_release(self):
        self.assertEqual(verdict(None, "", A)[0], "ok")

    def test_same_key(self):
        self.assertEqual(verdict("v0.0.1", notes(A), A)[0], "ok")

    def test_changed_key_is_error(self):
        level, message = verdict("v0.0.1", notes(A), B)
        self.assertEqual(level, "error")
        self.assertIn("v0.0.1", message)

    def test_missing_fingerprint_is_warning_only(self):
        self.assertEqual(verdict("v0.0.1", "без отпечатка", A)[0], "warning")


if __name__ == "__main__":
    unittest.main()
