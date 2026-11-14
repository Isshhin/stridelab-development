"""Run research-only counterfactuals and export the complete trace."""
import argparse
import json
from pathlib import Path
from .harness import evaluate_matrix

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--cases", type=Path, required=True)
    parser.add_argument("--policies", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    cases = json.loads(args.cases.read_text(encoding="utf8"))
    policies = json.loads(args.policies.read_text(encoding="utf8"))
    report = evaluate_matrix(cases, policies)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, sort_keys=True, allow_nan=False) + "\n", encoding="utf8")
    print(f"Exported {len(report['results'])} candidate scenario results to {args.output}")

if __name__ == "__main__":
    main()
