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
        fixture = self.directory / "TEST-fixture.xml"
        content = fixture.read_text()
        for case in sorted(report.CASES):
            content = content.replace('name="check()"', f'name="{case}"', 1)
        fixture.write_text(content)
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

    def test_duplicate_test_names_cannot_satisfy_parameterized_count(self):
        evidence = self.catalog["categories"][0]["evidence"][0]
        evidence.update(testNamePattern=r"\[[1-2]\] fixture", expectedCount=2,
                        expectedNames=["[1] fixture", "[2] fixture"])
        self.fixture(method="[1] fixture", count=2)
        result = report.aggregate(self.directory, self.catalog)
        self.assertEqual(result["status"], "FAIL")
        self.assertNotEqual(result["categories"][0]["status"], "PASS")

    def test_distinct_but_wrong_parameter_set_cannot_pass(self):
        evidence = self.catalog["categories"][0]["evidence"][0]
        evidence.update(testNamePattern=r"\[[1-2]\] fixture.*", expectedCount=2,
                        expectedNames=["[1] fixture A", "[2] fixture B"])
        self.fixture(method="[1] fixture A", count=2)
        fixture = self.directory / "TEST-fixture.xml"
        fixture.write_text(fixture.read_text().replace('[1] fixture A', '[2] fixture A', 1))
        self.assertEqual(report.aggregate(self.directory, self.catalog)["categories"][0]["status"], "NOT_RUN")

    def test_untrusted_suite_name_is_not_exported(self):
        self.fixture(suite="synthetic-secret.SecurityContext")
        self.assertNotIn("synthetic-secret", json.dumps(report.aggregate(self.directory, self.catalog)))

    def test_wrong_xml_root_cannot_pass(self):
        self.directory.joinpath("TEST-fixture.xml").write_text('<not-a-suite/>')
        self.assertEqual(report.aggregate(self.directory, self.catalog)["status"], "FAIL")

    def test_baseline_pass_record_cannot_override_failed_assertion(self):
        record = 'EVALUATION_RESULT ' + json.dumps({"case": "TOOL_SELECTION", "pass": True, "latencyNanos": 1})
        self.fixture(suite="test.AiEvaluationBaselineTests", method="TOOL_SELECTION", child="<failure/>", out=record)
        result = report.aggregate(self.directory, self.catalog)
        self.assertEqual(result["status"], "FAIL")
        self.assertNotEqual(result["baseline"][0]["status"], "PASS")

    def test_baseline_records_need_corresponding_test_identities(self):
        records = ['EVALUATION_RESULT ' + json.dumps({"case": case, "pass": True, "latencyNanos": 1})
                   for case in sorted(report.CASES)]
        self.fixture(suite="test.AiEvaluationBaselineTests", out='\n'.join(records), count=12)
        self.assertNotEqual(report.aggregate(self.directory, self.catalog)["baselineStatus"], "PASS")

    def test_declared_suite_count_must_match_evidence(self):
        self.fixture()
        fixture = self.directory / "TEST-fixture.xml"
        fixture.write_text(fixture.read_text().replace('<testsuite ', '<testsuite tests="2" '))
        self.assertEqual(report.aggregate(self.directory, self.catalog)["status"], "FAIL")

    def test_declared_suite_failure_cannot_be_ignored(self):
        self.fixture()
        fixture = self.directory / "TEST-fixture.xml"
        fixture.write_text(fixture.read_text().replace('<testsuite ', '<testsuite failures="1" '))
        self.assertEqual(report.aggregate(self.directory, self.catalog)["status"], "FAIL")

    def test_unknown_plain_method_names_are_not_exported(self):
        self.fixture(method="synthetic_secret()")
        self.assertNotIn("synthetic_secret", json.dumps(report.aggregate(self.directory, self.catalog)))

    def test_non_object_baseline_emission_is_safe_failure(self):
        self.fixture(suite="test.AiEvaluationBaselineTests", out="EVALUATION_RESULT []")
        self.assertEqual(report.aggregate(self.directory, self.catalog)["status"], "FAIL")

    def test_complete_baseline_cannot_pass_failed_execution(self):
        records = ['EVALUATION_RESULT ' + json.dumps({"case": case, "pass": True, "latencyNanos": 1})
                   for case in sorted(report.CASES)]
        self.directory.joinpath("TEST-fixture.xml").write_text(
            '<testsuite name="test.AiEvaluationBaselineTests">' +
            ''.join(f'<testcase name="{case}"/>' for case in sorted(report.CASES)) +
            '<system-out>' + '\n'.join(records) + '</system-out></testsuite>')
        result = report.aggregate(self.directory, self.catalog, execution_exit=1)
        self.assertEqual(result["status"], "FAIL")
        self.assertNotEqual(result["baselineStatus"], "PASS")
