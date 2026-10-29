import unittest

from version_step import next_steps, parse, verdict


class ParseTest(unittest.TestCase):
    def test_plain_and_tag(self):
        self.assertEqual(parse("0.2.3"), (0, 2, 3))
        self.assertEqual(parse("v4.2.2"), (4, 2, 2))

    def test_rejects_other_tags(self):
        for text in ["", "1.2", "v1.2.3.4", "release-1", "1.2.x", "1000.0.0"]:
            self.assertIsNone(parse(text), text)


class NextStepsTest(unittest.TestCase):
    def test_three_steps(self):
        self.assertEqual(next_steps((0, 2, 2)), [(0, 2, 3), (0, 3, 0), (1, 0, 0)])


class VerdictTest(unittest.TestCase):
    released = [(0, 0, 1), (0, 1, 0), (0, 2, 1), (0, 2, 2)]

    def level(self, new, released=None):
        return verdict(new, self.released if released is None else released)[0]

    def test_first_release(self):
        self.assertEqual(self.level((0, 0, 1), []), "ok")

    def test_allowed_steps(self):
        for new in [(0, 2, 3), (0, 3, 0), (1, 0, 0)]:
            self.assertEqual(self.level(new), "ok", new)

    def test_typo_in_major_is_refused(self):
        # Случай 29.10.2026: набрали 4.2.2 вместо 0.2.2.
        level, message = verdict((4, 2, 2), [(0, 2, 1)])
        self.assertEqual(level, "error")
        self.assertIn("0.2.2", message)

    def test_below_highest_is_refused_with_hint_to_delete(self):
        level, message = verdict((0, 2, 2), [(0, 2, 1), (4, 2, 2)])
        self.assertEqual(level, "error")
        self.assertIn("удалите", message)

    def test_already_released(self):
        self.assertEqual(self.level((0, 2, 2)), "error")

    def test_skipping_a_number_is_refused(self):
        self.assertEqual(self.level((0, 2, 4)), "error")
        self.assertEqual(self.level((0, 4, 0)), "error")
        self.assertEqual(self.level((0, 3, 1)), "error")

    def test_order_is_numeric_not_textual(self):
        self.assertEqual(self.level((0, 10, 0), [(0, 9, 3)]), "ok")
        # Самым высоким считается 0.9.10, а не 0.9.9 (как было бы при сравнении строк).
        self.assertEqual(self.level((0, 9, 11), [(0, 9, 9), (0, 9, 10)]), "ok")
        self.assertEqual(self.level((0, 9, 10), [(0, 9, 9), (0, 9, 10)]), "error")
        self.assertEqual(self.level((0, 9, 9), [(0, 9, 9), (0, 9, 10)]), "error")


if __name__ == "__main__":
    unittest.main()
