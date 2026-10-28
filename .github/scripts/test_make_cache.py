import json
import os
import subprocess
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
FIXTURE = os.path.join(HERE, "..", "..", "app", "src", "test", "resources", "open-meteo", "hannover.json")


class MakeCacheTest(unittest.TestCase):
    def run_script(self, now: int, age: int = 3) -> dict:
        with tempfile.TemporaryDirectory() as folder:
            output = os.path.join(folder, "cache.json")
            subprocess.run(
                [sys.executable, os.path.join(HERE, "make_cache.py"), FIXTURE, output,
                 "--now", str(now), "--age-hours", str(age)],
                check=True,
            )
            with open(output, encoding="utf-8") as file:
                return json.load(file)

    def test_first_hour_is_the_current_hour(self):
        now = 1_800_000_000 + 1234
        entry = self.run_script(now)
        raw = json.loads(entry["raw"])
        first = raw["hourly"]["time"][0]
        self.assertLessEqual(first, now)
        self.assertGreater(first + 3600, now)
        self.assertEqual(first % 3600, 0)
        self.assertEqual(len(raw["hourly"]["time"]), 49)

    def test_current_time_stays_in_the_first_hour(self):
        entry = self.run_script(1_800_000_000 + 1234)
        raw = json.loads(entry["raw"])
        first = raw["hourly"]["time"][0]
        self.assertTrue(first <= raw["current"]["time"] < first + 3600)

    def test_saved_age_and_place(self):
        now = 1_800_000_000
        entry = self.run_script(now, age=3)
        self.assertEqual(entry["v"], 1)
        self.assertEqual(entry["savedAtMs"], (now - 3 * 3600) * 1000)
        self.assertEqual(entry["name"], "Hannover")
        self.assertAlmostEqual(entry["lat"], 52.37)
        self.assertAlmostEqual(entry["lon"], 9.74)

    def test_values_are_not_changed(self):
        with open(FIXTURE, encoding="utf-8") as file:
            original = json.load(file)
        raw = json.loads(self.run_script(1_800_000_000)["raw"])
        self.assertEqual(raw["hourly"]["temperature_2m"], original["hourly"]["temperature_2m"])
        self.assertEqual(raw["current"]["temperature_2m"], original["current"]["temperature_2m"])
        self.assertEqual(raw["timezone"], original["timezone"])


if __name__ == "__main__":
    unittest.main()
