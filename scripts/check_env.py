#!/usr/bin/env python3
"""Report keys that .env is missing compared to .env.example.

.env is not tracked by git, so it drifts silently: a new variable added to .env.example
never reaches an existing checkout, and the symptom shows up far from the cause. That is
exactly how SPRING_PROFILES_ACTIVE went unset for this project — the prod profile was
never applied, leaving Swagger publicly readable, and nothing pointed at the reason.

Usage:
    python scripts/check_env.py           # exits 1 if anything is missing
    python scripts/check_env.py --fix     # appends the missing keys with example values
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
EXAMPLE = ROOT / ".env.example"
ENV = ROOT / ".env"


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
    if not EXAMPLE.exists():
        print(f"error: {EXAMPLE.name} not found", file=sys.stderr)
        return 2
    if not ENV.exists():
        print(f"error: {ENV.name} not found — copy {EXAMPLE.name} to .env and fill it in",
              file=sys.stderr)
        return 2

    example = read_entries(EXAMPLE)
    env = read_entries(ENV)
    missing = [k for k in example if k not in env]

    if not missing:
        print(f"ok: .env has all {len(example)} keys from .env.example")
        return 0

    print(f"missing {len(missing)} key(s) in .env:")
    for key in missing:
        print(f"  {key}")

    if "--fix" not in sys.argv:
        print("\nrun with --fix to append them with the values from .env.example")
        return 1

    with ENV.open("a", encoding="utf-8", newline="\n") as handle:
        handle.write("\n# Added by scripts/check_env.py --fix\n")
        for key in missing:
            handle.write(f"{key}={example[key]}\n")
    print(f"\nappended {len(missing)} key(s). Review the values before starting the stack —"
          " secrets are blank in .env.example, and SPRING_PROFILES_ACTIVE should be prod"
          " when running via docker compose.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
