import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("evaluation_report", Path(__file__).with_name("report.py"))
report = importlib.util.module_from_spec(spec)
spec.loader.exec_module(report)


class ReportTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        self.catalog = {"categories": [{"id": "boundary", "scope": "test",
                        "evidence": [{"suite": "test.Boundary", "methodPrefix": "check"}]}],
                        "manualReview": ["semantics"], "blocked": ["bedrock"]}

    def fixture(self, child="", method="check()", suite="test.Boundary", out="", count=1):
        self.directory.joinpath("TEST-fixture.xml").write_text(
            f'<testsuite name="{suite}">' +
            ''.join(f'<testcase name="{method}">{child}</testcase>' for _ in range(count)) +
            f'<system-out>{out}</system-out></testsuite>')

    def test_missing_results_are_not_run(self):
        result = report.aggregate(self.directory, self.catalog)
        self.assertEqual(result["status"], "NOT_RUN")
        self.assertEqual(result["categories"][0]["status"], "NOT_RUN")

    def test_pass_requires_matching_assertion(self):
        self.fixture()
        self.assertEqual(report.aggregate(self.directory, self.catalog)["categories"][0]["status"], "PASS")

    def test_missing_method_cannot_pass(self):
        self.fixture(method="other()")
        self.assertEqual(report.aggregate(self.directory, self.catalog)["categories"][0]["status"], "NOT_RUN")

    def test_skipped_cannot_pass(self):
        self.fixture("<skipped/>")
        self.assertEqual(report.aggregate(self.directory, self.catalog)["categories"][0]["status"], "NOT_RUN")

    def test_failures_are_safe_and_nonpassing(self):
        self.fixture('<failure message="synthetic-secret">synthetic-secret</failure>', out="synthetic-secret")
        result = report.aggregate(self.directory, self.catalog)
        self.assertEqual(result["status"], "FAIL")
        self.assertEqual(result["categories"][0]["status"], "FAIL")
        self.assertNotIn("synthetic-secret", json.dumps(result))

    def test_stale_results_cannot_pass(self):
        self.fixture()
        result = report.aggregate(self.directory, self.catalog, since=9999999999)
        self.assertEqual(result["counts"]["total"], 0)
        self.assertEqual(result["status"], "NOT_RUN")

    def test_execution_failure_cannot_pass(self):
        self.fixture()
        self.assertEqual(report.aggregate(self.directory, self.catalog, execution_exit=1)["status"], "FAIL")

    def test_invalid_xml_is_fail(self):
        self.directory.joinpath("TEST-fixture.xml").write_text("<bad")
        self.assertEqual(report.aggregate(self.directory, self.catalog)["status"], "FAIL")

    def test_manual_and_blocked_are_never_pass(self):
        result = report.aggregate(self.directory, self.catalog)
        self.assertEqual(result["categories"][-2]["status"], "MANUAL_REVIEW")
        self.assertEqual(result["categories"][-1]["status"], "BLOCKED")

    def test_baseline_requires_all_twelve_unique_cases_and_sanitizes_metadata(self):
        records = ["EVALUATION_RESULT " + json.dumps(
            {"case": case, "pass": True, "latencyNanos": 12, "input": "synthetic-secret"})
            for case in sorted(report.CASES)]
        self.fixture(suite="test.AiEvaluationBaselineTests", out="\n".join(records), count=12)
        result = report.aggregate(self.directory, self.catalog)
        self.assertEqual(result["baselineStatus"], "PASS")
        self.assertEqual(len(result["baseline"]), 12)
        self.assertNotIn("synthetic-secret", json.dumps(result))
        self.assertIsNone(result["baseline"][0]["tokenUsage"])
        self.fixture(suite="test.AiEvaluationBaselineTests", out="\n".join(records[:-1]))
        self.assertEqual(report.aggregate(self.directory, self.catalog)["baselineStatus"], "NOT_RUN")

    def test_parameterized_display_names_require_every_expected_invocation(self):
        evidence = self.catalog["categories"][0]["evidence"][0]
        evidence["testNamePattern"] = r"\[[1-2]\] fixture"
        evidence["expectedCount"] = 2
        self.fixture(method="[1] fixture")
        self.assertEqual(report.aggregate(self.directory, self.catalog)["categories"][0]["status"], "NOT_RUN")

    def test_parameterized_argument_values_are_not_exported(self):
        self.fixture(method='[1] argument = synthetic-secret')
        self.assertNotIn("synthetic-secret", json.dumps(report.aggregate(self.directory, self.catalog)))

    def test_duplicate_baseline_evidence_cannot_pass(self):
        records = ["EVALUATION_RESULT " + json.dumps(
            {"case": "TOOL_SELECTION", "pass": True, "latencyNanos": 1})] * 12
        self.fixture(suite="test.AiEvaluationBaselineTests", out="\n".join(records), count=12)
        result = report.aggregate(self.directory, self.catalog)
        self.assertEqual(result["status"], "FAIL")
        self.assertNotEqual(result["baselineStatus"], "PASS")
