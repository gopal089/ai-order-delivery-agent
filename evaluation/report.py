"""Safe aggregation of existing Gradle/JUnit evidence; no second AI/evaluation engine."""
import argparse
import hashlib
from datetime import datetime, timezone
import json
import re
from pathlib import Path
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
CASES = {"TOOL_SELECTION", "ARGUMENTS", "AUTHORIZATION", "CURRENT_RETRIEVAL",
         "UNSUPPORTED_CLAIM", "PROVIDER_FAILURE", "MISSING_LOCATION", "PROMPT_INJECTION",
         "GROUNDING_CONFLICT", "MODEL_TIMEOUT", "TOOL_TIMEOUT", "MAX_TOOL_CALLS"}


def junit_status(case):
    if case.find("failure") is not None or case.find("error") is not None:
        return "FAIL"
    if case.find("skipped") is not None:
        return "NOT_RUN"
    return "PASS"


def aggregate(directory, catalog, since=None, execution_exit=None):
    """Never copy system-out, prompts, exception text, tokens or credentials into results."""
    tests, baseline = [], []
    invalid = False
    identities = set()
    for path in sorted(Path(directory).glob("TEST-*.xml")):
        if since is not None and path.stat().st_mtime < since:
            continue  # stale reports are not evidence of this run
        try:
            suite = ET.parse(path).getroot()
            cases = suite.findall("testcase")
            if (suite.tag != "testsuite" or not suite.get("name")
                    or any(not case.get("name") for case in cases)
                    or (suite.get("tests") is not None and int(suite.get("tests")) != len(cases))):
                raise ValueError()
            for attribute, child in (("failures", "failure"), ("errors", "error"), ("skipped", "skipped")):
                if (suite.get(attribute) is not None and
                        int(suite.get(attribute)) != sum(case.find(child) is not None for case in cases)):
                    raise ValueError()
        except (ET.ParseError, OSError, ValueError):
            invalid = True
            continue
        for case in suite.findall("testcase"):
            identity = (suite.get("name"), case.get("name"))
            if identity in identities:
                invalid = True
            identities.add(identity)
            tests.append({"suite": suite.get("name", ""), "test": case.get("name", ""),
                          "status": junit_status(case)})
        if suite.get("name", "").endswith(".AiEvaluationBaselineTests"):
            for line in (suite.findtext("system-out") or "").splitlines():
                if not line.startswith("EVALUATION_RESULT "):
                    continue
                try:
                    record = json.loads(line.removeprefix("EVALUATION_RESULT "))
                    if not isinstance(record, dict):
                        raise ValueError()
                    case_id = record["case"]
                    latency = record["latencyNanos"]
                    if (case_id not in CASES or record.get("pass") is not True
                            or type(latency) is not int or latency < 0):
                        raise ValueError()
                    baseline.append({"case": case_id, "status": "PASS",
                                     "measuredCaseLatencyNanos": latency,
                                     "model": "deterministic-test-fixture",
                                     "tokenUsage": None, "estimatedCost": None})
                except (KeyError, ValueError, TypeError):
                    invalid = True
    categories = []
    for category in catalog["categories"]:
        matched, missing = [], False
        for evidence in category["evidence"]:
            found = [t for t in tests if t["suite"] == evidence["suite"]
                     and (re.fullmatch(evidence["testNamePattern"], t["test"])
                          if "testNamePattern" in evidence else
                          t["test"].startswith(evidence["methodPrefix"] + "("))]
            if (len(found) != evidence.get("expectedCount", 1)
                    or len({t["test"] for t in found}) != len(found)
                    or ("expectedNames" in evidence and
                        {t["test"] for t in found} != set(evidence["expectedNames"]))):
                missing = True
            matched.extend(found)
        status = ("FAIL" if any(t["status"] == "FAIL" for t in matched) else
                  "NOT_RUN" if missing or any(t["status"] == "NOT_RUN" for t in matched) else "PASS")
        categories.append({"id": category["id"], "status": status,
                           "scope": category["scope"], "evidence": matched})
    categories.extend({"id": item, "status": "MANUAL_REVIEW"} for item in catalog["manualReview"])
    categories.extend({"id": item, "status": "BLOCKED"} for item in catalog["blocked"])
    failures = sum(t["status"] == "FAIL" for t in tests)
    skipped = sum(t["status"] == "NOT_RUN" for t in tests)
    baseline_tests = [t for t in tests if t["suite"].endswith(".AiEvaluationBaselineTests")]
    baseline_complete = (len(baseline) == 12 and {b["case"] for b in baseline} == CASES
                         and len(baseline_tests) == 12
                         and {t["test"] for t in baseline_tests} == CASES
                         and all(t["status"] == "PASS" for t in baseline_tests))
    if len({b["case"] for b in baseline}) != len(baseline):
        invalid = True
    overall = ("FAIL" if invalid or failures or execution_exit not in (None, 0) else
               "NOT_RUN" if not tests or skipped or not baseline_complete else "PASS")
    for record in baseline:
        matching = [t for t in baseline_tests if t["test"] == record["case"]]
        record["status"] = ("FAIL" if invalid or execution_exit not in (None, 0)
                            or any(t["status"] == "FAIL" for t in matching) else
                            "PASS" if len(matching) == 1 and matching[0]["status"] == "PASS" else "NOT_RUN")
    # Category evidence shares these references; strip values only AFTER matching invocations.
    trusted_suites = {e["suite"] for c in catalog["categories"] for e in c["evidence"]}
    trusted_suites.add("com.aiorderdeliveryagent.backend.ai.AiEvaluationBaselineTests")
    trusted_methods = {(e["suite"], e["methodPrefix"] + "()")
                       for c in catalog["categories"] for e in c["evidence"] if "methodPrefix" in e}
    for test in tests:
        if (test["suite"], test["test"]) not in trusted_methods:
            test["test"] = "test-" + hashlib.sha256(test["test"].encode()).hexdigest()[:16]
        if test["suite"] not in trusted_suites:
            test["suite"] = "suite-" + hashlib.sha256(test["suite"].encode()).hexdigest()[:16]
    return {"schemaVersion": 2, "status": overall,
            "generatedAt": datetime.now(timezone.utc).isoformat(),
            "evidenceMode": "fresh-execution" if since is not None else "imported-results-not-freshness-verified",
            "executionExitCode": execution_exit,
            "counts": {"total": len(tests), "passed": len(tests)-failures-skipped,
                       "failed": failures, "notRun": skipped},
            "baselineStatus": ("FAIL" if invalid or failures or execution_exit not in (None, 0) else
                               "PASS" if baseline_complete else "NOT_RUN"),
            "baseline": baseline, "categories": categories,
            "invalidReports": invalid, "tests": tests,
            "limitations": ["No real model/provider invocation", "No accuracy score",
                            "Case latency includes assertions, not a model latency benchmark",
                            "Null token usage/cost are unavailable, not zero",
                            "PASS covers observed assertions only; not production readiness"]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run", action="store_true", help="Run Gradle before collecting fresh evidence")
    parser.add_argument("--scope", choices=["full", "baseline"], default="baseline")
    parser.add_argument("--results", type=Path, default=ROOT / "backend/build/test-results/test")
    args = parser.parse_args()
    output_dir = ROOT / "backend/build/reports/evaluation"
    output_dir.mkdir(parents=True, exist_ok=True)
    since, code, command = None, None, None
    if args.run:
        since = time.time()
        command = ["./gradlew", "test"]
        if args.scope == "baseline":
            command += ["--tests", "*AiEvaluationBaselineTests"]
        command += ["--rerun-tasks", "--console=plain"]
        # Raw generated diagnostics stay ignored and local; never echoed or copied to JSON.
        with (output_dir / (args.scope + ".gradle.log")).open("w") as log:
            try:
                code = subprocess.run(command, cwd=ROOT / "backend", stdout=log,
                                      stderr=subprocess.STDOUT, timeout=900).returncode
            except (OSError, subprocess.TimeoutExpired):
                code = 1
    catalog = json.loads((ROOT / "evaluation/catalog.json").read_text())
    report = aggregate(args.results, catalog, since, code)
    report["command"] = command
    report["scope"] = args.scope
    if args.scope == "full":
        automated = [c for c in report["categories"] if "evidence" in c]
        if any(c["status"] == "FAIL" for c in automated):
            report["status"] = "FAIL"
        elif report["status"] == "PASS" and any(c["status"] == "NOT_RUN" for c in automated):
            report["status"] = "NOT_RUN"
    report["gitCommit"] = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    report["workingTreeChanged"] = bool(subprocess.check_output(["git", "status", "--porcelain"], cwd=ROOT, text=True).strip())
    destination = output_dir / (args.scope + ".json")
    destination.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps({"status": report["status"], "counts": report["counts"],
                      "baselineStatus": report["baselineStatus"], "report": str(destination)}))
    return 1 if report["status"] == "FAIL" else 2 if report["status"] == "NOT_RUN" else 0


if __name__ == "__main__":
    raise SystemExit(main())
