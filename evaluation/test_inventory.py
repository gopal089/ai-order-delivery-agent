import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parent


class InventoryTests(unittest.TestCase):
    def test_cost_inventory_is_not_a_bill_or_priced_estimate(self):
        data = json.loads((ROOT / "cost-drivers.json").read_text())
        self.assertIsNone(data["estimate"])
        self.assertIsNone(data["currentAccountCost"]["amount"])
        self.assertEqual(data["currentAccountCost"]["status"], "NOT VERIFIED")
        self.assertEqual(data["resourcesCreatedByThisBatch"], 0)
        self.assertFalse(data["awsAccessPerformed"])
        self.assertEqual(len(data["drivers"]), 16)
        for driver in data["drivers"]:
            self.assertIsNone(driver["price"])
            self.assertIsNone(driver["usage"])
            self.assertTrue(driver["variables"])
            self.assertTrue(driver["controls"])

    def test_catalog_parameter_sets_are_explicit_and_unique(self):
        data = json.loads((ROOT / "catalog.json").read_text())
        self.assertEqual(len({c["id"] for c in data["categories"]}), len(data["categories"]))
        for category in data["categories"]:
            for evidence in category["evidence"]:
                if evidence.get("expectedCount", 1) > 1:
                    names = evidence["expectedNames"]
                    self.assertEqual(len(set(names)), evidence["expectedCount"])

    def test_twenty_requested_ai_areas_have_explicit_evidence(self):
        data = json.loads((ROOT / "catalog.json").read_text())
        ids = {c["id"] for c in data["categories"]}
        self.assertTrue({"tool_selection", "tool_argument_validation", "authorization_boundary",
            "user_authorization", "current_external_retrieval", "missing_current_retrieval",
            "unsupported_claims", "conflicting_model_claims", "missing_location", "provider_failure",
            "provider_timeout", "tool_timeout", "tool_call_limits", "prompt_injection",
            "malicious_provider_content", "conversation_persistence", "tenant_isolation",
            "grounding_provenance", "stale_history", "model_unavailable", "provider_unavailable"} <= ids)
