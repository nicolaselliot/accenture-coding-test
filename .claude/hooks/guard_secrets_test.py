#!/usr/bin/env python3
"""Self-test for guard_secrets.py. Run: python3 .claude/hooks/guard_secrets_test.py

Exists because this hook is a security control that runs on every Bash call: a
false negative ships a secret, and a false positive locks the agent out of the
repository. Both directions need coverage, so the allow cases below matter as
much as the block cases.
"""

import json
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
GUARD = os.path.join(HERE, "guard_secrets.py")

BLOCK, ALLOW = 2, 0

CASES = [
    # (label, tool_name, tool_input, expected exit)
    # --- file tools -------------------------------------------------------
    ("Write absolute local.properties", "Write", {"file_path": "/x/local.properties"}, BLOCK),
    ("Write bare local.properties", "Write", {"file_path": "local.properties"}, BLOCK),
    ("Write ./local.properties", "Write", {"file_path": "./local.properties"}, BLOCK),
    ("Write service-account.json", "Write", {"file_path": "/x/service-account.json"}, BLOCK),
    ("Write service-account-prod.json", "Write", {"file_path": "sa/service-account-prod.json"}, BLOCK),
    ("Write upload.jks", "Write", {"file_path": "app/upload.jks"}, BLOCK),
    ("Write signing.pem", "Write", {"file_path": "certs/signing.pem"}, BLOCK),
    ("Write api.key", "Write", {"file_path": "api.key"}, BLOCK),
    ("Write Config.xcconfig", "Write", {"file_path": "iosApp/Config.xcconfig"}, BLOCK),
    ("Write bare .env", "Write", {"file_path": ".env"}, BLOCK),
    ("Write .env.production", "Write", {"file_path": ".env.production"}, BLOCK),
    ("Write GoogleService-Info.plist", "Write", {"file_path": "iosApp/GoogleService-Info.plist"}, BLOCK),
    ("Edit id_rsa", "Edit", {"file_path": "/home/u/.ssh/id_rsa"}, BLOCK),
    ("NotebookEdit sensitive notebook_path", "NotebookEdit",
     {"notebook_path": "/x/local.properties"}, BLOCK),
    ("NotebookEdit ordinary notebook", "NotebookEdit",
     {"notebook_path": "analysis/notebook.ipynb"}, ALLOW),
    ("Write ordinary Kotlin file", "Write", {"file_path": "domain/src/Repo.kt"}, ALLOW),
    ("Write gradle catalog", "Write", {"file_path": "gradle/libs.versions.toml"}, ALLOW),
    ("Write README.pemx (not a .pem)", "Write", {"file_path": "README.pemx"}, ALLOW),
    ("Write keystore.md (docs, not a keystore)", "Write", {"file_path": "docs/keystore.md"}, ALLOW),

    # --- bash: writes -----------------------------------------------------
    ("bash redirect into local.properties", "Bash", {"command": "cat > local.properties"}, BLOCK),
    ("bash redirect no space", "Bash", {"command": "cat >local.properties"}, BLOCK),
    ("bash append into .env", "Bash", {"command": "echo TOKEN=x >> .env"}, BLOCK),
    ("bash heredoc into local.properties", "Bash", {"command": "cat > local.properties << 'EOF'"}, BLOCK),
    ("bash tee", "Bash", {"command": "echo x | tee local.properties"}, BLOCK),
    ("bash cp into keystore", "Bash", {"command": "cp /tmp/a app/upload.jks"}, BLOCK),
    ("bash mv into .env", "Bash", {"command": "mv template .env"}, BLOCK),
    ("bash sed in place", "Bash", {"command": "sed -i '' s/a/b/ local.properties"}, BLOCK),
    ("bash dd of=", "Bash", {"command": "dd if=/dev/zero of=secrets.properties"}, BLOCK),
    ("bash touch", "Bash", {"command": "touch .env.local"}, BLOCK),
    ("bash second command after &&", "Bash", {"command": "ls && cat > .env"}, BLOCK),
    ("bash sudo wrapper", "Bash", {"command": "sudo tee /etc/x/id_rsa"}, BLOCK),
    # Heredoc body is treated as data, so prove a redirect after the marker is
    # still caught rather than hidden by that exemption.
    ("bash redirect after heredoc marker", "Bash",
     {"command": "cat << 'EOF' > local.properties\nx=1\nEOF"}, BLOCK),
    ("bash pipeline resumes checking after heredoc", "Bash",
     {"command": "cat > .gitignore << 'EOF'\nlocal.properties\nEOF\ncat local.properties"}, BLOCK),

    # --- bash: reads (exfiltration) ---------------------------------------
    ("bash cat a secret", "Bash", {"command": "cat local.properties"}, BLOCK),
    ("bash grep a secret", "Bash", {"command": "grep -i token local.properties"}, BLOCK),
    ("bash base64 a keystore", "Bash", {"command": "base64 app/upload.jks"}, BLOCK),
    ("bash keytool on a keystore", "Bash", {"command": "keytool -list -keystore app/upload.jks"}, BLOCK),
    ("bash git add a secret", "Bash", {"command": "git add local.properties"}, BLOCK),
    # shlex.split() alone glues an unspaced operator onto the filename
    # (`local.properties|base64` stays one token), so a naive reader/writer
    # check never sees a clean basename to match against. These prove the
    # tokenizer itself splits on the operator, not just on whitespace.
    ("bash pipe glued to reader, no space", "Bash",
     {"command": "cat local.properties|base64"}, BLOCK),
    ("bash semicolon glued to reader, no space", "Bash",
     {"command": "cat local.properties;true"}, BLOCK),
    ("bash and-and glued to reader, no space", "Bash",
     {"command": "cat local.properties&&true"}, BLOCK),
    ("bash redirect glued with fd prefix, no space", "Bash",
     {"command": "cmd 2>>local.properties"}, BLOCK),

    # --- bash: must stay allowed ------------------------------------------
    # PR1 has to put these names *into* .gitignore. If the guard blocks that, it
    # gets disabled, and then it protects nothing.
    ("gitignore via echo", "Bash", {"command": "echo 'local.properties' >> .gitignore"}, ALLOW),
    ("gitignore via printf", "Bash", {"command": "printf '%s\\n' local.properties .env > .gitignore"}, ALLOW),
    ("gitignore heredoc", "Bash", {"command": "cat > .gitignore << 'EOF'\nlocal.properties\n*.jks\nEOF"}, ALLOW),
    ("check-ignore is read-only metadata", "Bash", {"command": "ls -la local.properties"}, ALLOW),
    ("ordinary gradle build", "Bash", {"command": "./gradlew :androidApp:assembleDebug"}, ALLOW),
    ("ordinary git status", "Bash", {"command": "git status --porcelain"}, ALLOW),
    ("grep in source", "Bash", {"command": "grep -r subscribers_count data/github/src"}, ALLOW),
    ("write a normal file", "Bash", {"command": "cat > README.md"}, ALLOW),
    # Searching *for* the name is legitimate; opening the file is not. The
    # difference is argument position, so both directions are pinned here.
    ("grep for the name inside gitignore", "Bash",
     {"command": "grep local.properties .gitignore"}, ALLOW),
    ("grep a pattern out of the secret", "Bash",
     {"command": "grep token local.properties"}, BLOCK),

    # --- fail-closed behaviour --------------------------------------------
    ("no tool_input", "Bash", {}, ALLOW),
]

# Tools the hook must actually be invoked for. Listing "NotebookEdit" in
# guard_secrets.py's own docstring and testing it above is worthless if
# .claude/settings.json never fires the hook for it in the first place.
REQUIRED_MATCHER_TOOLS = {"Write", "Edit", "MultiEdit", "NotebookEdit", "Bash", "Read"}
SETTINGS = os.path.join(HERE, os.pardir, "settings.json")


def missing_matcher_tools():
    """Tools in REQUIRED_MATCHER_TOOLS that settings.json does not wire the guard to."""
    with open(SETTINGS, encoding="utf-8") as handle:
        settings = json.load(handle)
    matcher = settings["hooks"]["PreToolUse"][0]["matcher"]
    covered = set(matcher.split("|"))
    return REQUIRED_MATCHER_TOOLS - covered


def run(tool_name, tool_input):
    payload = json.dumps({"tool_name": tool_name, "tool_input": tool_input})
    proc = subprocess.run(
        [sys.executable, GUARD], input=payload, capture_output=True, text=True
    )
    return proc.returncode


def main():
    failures = []
    for label, tool_name, tool_input, expected in CASES:
        actual = run(tool_name, tool_input)
        if actual != expected:
            failures.append((label, expected, actual))

    # Malformed input must block, not allow.
    proc = subprocess.run(
        [sys.executable, GUARD], input="not json at all", capture_output=True, text=True
    )
    if proc.returncode != BLOCK:
        failures.append(("malformed json fails closed", BLOCK, proc.returncode))

    proc = subprocess.run(
        [sys.executable, GUARD], input="", capture_output=True, text=True
    )
    if proc.returncode != BLOCK:
        failures.append(("empty stdin fails closed", BLOCK, proc.returncode))

    missing = missing_matcher_tools()
    if missing:
        failures.append((
            "settings.json matcher covers %s" % ", ".join(sorted(missing)), 0, len(missing)
        ))

    total = len(CASES) + 3
    if failures:
        for label, expected, actual in failures:
            print("FAIL  %-45s expected %d, got %d" % (label, expected, actual))
        print("\n%d/%d passed, %d FAILED" % (total - len(failures), total, len(failures)))
        return 1

    print("%d/%d passed" % (total, total))
    return 0


if __name__ == "__main__":
    sys.exit(main())
