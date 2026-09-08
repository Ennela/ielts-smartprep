#!/usr/bin/env python3
"""Report keys that .env is missing compared to .env.example.

.env is not tracked by git, so it drifts silently: a new variable added to .env.example
never reaches an existing checkout, and the symptom shows up far from the cause. That is
exactly how SPRING_PROFILES_ACTIVE went unset for this project — the prod profile was
never applied, leaving Swagger publicly readable, and nothing pointed at the reason.

The production stack has its own pair of files, so the two paths are arguments rather than
constants.

Usage:
    python scripts/check_env.py           # exits 1 if anything is missing
    python scripts/check_env.py --fix     # appends the missing keys with example values
    python scripts/check_env.py --example .env.prod.example --env .env.prod
"""
import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def read_entries(path):
    """Return {KEY: value} for simple KEY=value lines, ignoring comments and blanks."""
    entries = {}
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            continue
        key, _, value = stripped.partition("=")
        key = key.strip()
        if key.isupper() or "_" in key:
            entries[key] = value
    return entries


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--example", default=".env.example",
                        help="template to compare against (default: .env.example)")
    parser.add_argument("--env", default=".env",
                        help="file to check for missing keys (default: .env)")
    parser.add_argument("--fix", action="store_true",
                        help="append the missing keys with the template's values")
    args = parser.parse_args()

    example_path = ROOT / args.example
    env_path = ROOT / args.env

    if not example_path.exists():
        print(f"error: {example_path.name} not found", file=sys.stderr)
        return 2
    if not env_path.exists():
        print(f"error: {env_path.name} not found — copy {example_path.name} to "
              f"{env_path.name} and fill it in", file=sys.stderr)
        return 2

    example = read_entries(example_path)
    env = read_entries(env_path)
    missing = [k for k in example if k not in env]

    if not missing:
        print(f"ok: {env_path.name} has all {len(example)} keys from {example_path.name}")
        return 0

    print(f"missing {len(missing)} key(s) in {env_path.name}:")
    for key in missing:
        print(f"  {key}")

    if not args.fix:
        print(f"\nrun with --fix to append them with the values from {example_path.name}")
        return 1

    with env_path.open("a", encoding="utf-8", newline="\n") as handle:
        handle.write("\n# Added by scripts/check_env.py --fix\n")
        for key in missing:
            handle.write(f"{key}={example[key]}\n")
    print(f"\nappended {len(missing)} key(s). Review the values before starting the stack —"
          " secrets are blank in .env.example, and SPRING_PROFILES_ACTIVE should be prod"
          " when running via docker compose.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
