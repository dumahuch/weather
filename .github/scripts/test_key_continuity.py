import unittest

from key_continuity import MARKER, fingerprint_from_notes, pinned_fingerprint, verdict

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


class PinnedTest(unittest.TestCase):
    def test_reads_fingerprint_skipping_comments(self):
        self.assertEqual(pinned_fingerprint(f"# комментарий {B}\n{A}\n"), A)

    def test_no_fingerprint(self):
        self.assertIsNone(pinned_fingerprint("# только комментарий\n"))

    def test_pinned_mismatch_is_error_even_if_notes_agree(self):
        level, message = verdict("v0.0.1", notes(B), B, pinned=A)
        self.assertEqual(level, "error")
        self.assertIn("key-fingerprint", message)

    def test_pinned_match_replaces_missing_notes_warning(self):
        self.assertEqual(verdict("v0.0.1", "без отпечатка", A, pinned=A)[0], "ok")

    def test_pinned_match_first_release(self):
        self.assertEqual(verdict(None, "", A, pinned=A)[0], "ok")


if __name__ == "__main__":
    unittest.main()
