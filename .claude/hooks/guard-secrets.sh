#!/usr/bin/env bash
# PreToolUse guard: blocks agent reads and writes of secret or signing material.
#
# Thin wrapper. The logic lives in guard_secrets.py, which has a self-test:
#   python3 .claude/hooks/guard_secrets_test.py
#
# Exit 2 blocks the tool call and reports the reason back to Claude. Every
# failure path exits 2 — including "python3 is missing" and "stdin was not
# JSON". A security control whose error path is "allow" is worse than no
# control, because it is trusted.
set -uo pipefail

guard="$(dirname "${BASH_SOURCE[0]}")/guard_secrets.py"

if ! command -v python3 >/dev/null 2>&1; then
  echo "BLOCKED: guard-secrets needs python3 and cannot find it, so it cannot" >&2
  echo "verify this call. Failing closed. Install python3 or fix PATH." >&2
  exit 2
fi

if [[ ! -r "$guard" ]]; then
  echo "BLOCKED: guard-secrets cannot read $guard. Failing closed." >&2
  exit 2
fi

exec python3 "$guard"
