#!/usr/bin/env python3
"""Blocks agent reads and writes of secret or signing material.

Contract with Claude Code: exit 0 allows the tool call, exit 2 blocks it and feeds
stderr back to the model. Every failure path here exits 2 on purpose — a security
control whose error path is "allow" is worse than no control, because it is trusted.

Two things this guards that a file-tool-only hook does not:

- `Bash`. `cat > local.properties <<EOF` is the normal way an agent edits files in
  some modes, so a guard matching only Write|Edit is theatre.
- Reads, not just writes. `cat local.properties` does not create a secret, but it
  copies one into the transcript, which is its own disclosure.

It deliberately does *not* block a secret filename merely being mentioned, because
PR1 has to write `local.properties` into `.gitignore`. Only the target of a write
and the argument of a reader count.
"""

import json
import os
import re
import shlex
import sys

# Basenames no agent should author, read, or stage. Anchored as full-basename
# matches so `mykey.pem` is caught and `README.pemx` is not.
SENSITIVE_PATTERNS = (
    r"local\.properties",
    r"secrets\.properties",
    r"signing\.properties",
    r"keystore\.properties",
    r".*\.jks",
    r".*\.keystore",
    r".*\.jceks",
    r".*\.p12",
    r".*\.pfx",
    r".*\.p8",
    r".*\.pem",
    r".*\.key",
    r".*\.cer",
    r".*\.der",
    r".*\.mobileprovision",
    r".*\.xcconfig",  # common home for iOS API keys
    r"id_rsa.*",
    r"id_ed25519.*",
    r"\.env",
    r"\.env\..*",
    r"google-services\.json",
    r"GoogleService-Info\.plist",
    r"service-account.*\.json",  # Firebase/GCP deploy credentials — PR16 needs one
)
SENSITIVE = re.compile(r"^(?:%s)$" % "|".join(SENSITIVE_PATTERNS), re.IGNORECASE)

# Argument positions that are written to.
WRITERS = {
    "tee", "cp", "mv", "touch", "install", "ln", "truncate",
    "dd", "shred", "rsync", "scp", "unzip", "tar",
}
# Argument positions that are read out of.
READERS = {
    "cat", "bat", "less", "more", "head", "tail", "nl", "cut", "sort", "uniq",
    "grep", "egrep", "fgrep", "rg", "ag", "awk", "strings", "base64", "xxd",
    "od", "openssl", "keytool", "security", "plutil", "defaults", "jq", "yq",
}
# sed/perl read by default and write with -i; either way the file is touched.
EITHER = {"sed", "perl", "ruby", "python", "python3"}
# Commands whose first non-flag argument is a pattern or script, not a path.
# Without this, `grep local.properties .gitignore` — searching for the *string*,
# which is legitimate PR1 work — reads as an attempt to open the secret.
PATTERN_FIRST = {"grep", "egrep", "fgrep", "rg", "ag", "awk", "sed", "perl"}
# Wrappers that prefix a real command — step over them to find the actual verb.
WRAPPERS = {"sudo", "env", "command", "nohup", "time", "xargs", "nice", "stdbuf"}
# Shell operators that start a fresh command, so the verb has to be re-read.
SEPARATORS = {"|", "||", "&&", ";", "&", "(", ")", "{", "}"}
REDIRECT_TOKENS = {">", ">>", "1>", "2>", "&>", ">|", "1>>", "2>>", "&>>"}


def is_sensitive(path):
    """True if `path`'s basename names secret or signing material."""
    cleaned = path.strip().strip("'\"")
    if not cleaned:
        return False
    return SENSITIVE.match(os.path.basename(cleaned)) is not None


def offending_paths_in_command(command):
    """Sensitive paths this shell command would write to or read from."""
    try:
        tokens = shlex.split(command, comments=False)
    except ValueError:
        # Unbalanced quoting: we cannot reason about structure, so fall back to a
        # substring scan and block on any mention at all. Fail closed.
        for pattern in SENSITIVE_PATTERNS:
            if re.search(pattern, command, re.IGNORECASE):
                return ["<unparseable command referencing secret material>"]
        return []

    hits = []
    verb = None
    pattern_arg_pending = False
    # Everything after a heredoc marker is *body* — data being written into the
    # redirect target, not a list of paths. PR1 writes `local.properties` into
    # `.gitignore` exactly this way, and a guard that blocks that gets disabled.
    # Redirections are still honoured inside the body so
    # `cat << EOF > local.properties` cannot sneak past.
    in_heredoc_body = False
    heredoc_delimiter = None
    index = 0
    while index < len(tokens):
        token = tokens[index]

        # The closing delimiter ends the body; commands after it are real again.
        # Without this, one `cat > .gitignore <<EOF … EOF` early in a multi-line
        # script would exempt every command after it.
        if in_heredoc_body and token == heredoc_delimiter:
            in_heredoc_body = False
            heredoc_delimiter = None
            verb = None
            index += 1
            continue

        if token in SEPARATORS:
            verb = None
            in_heredoc_body = False
            heredoc_delimiter = None
            index += 1
            continue

        if token in ("<<", "<<-"):
            if index + 1 < len(tokens):
                heredoc_delimiter = tokens[index + 1].strip("'\"")
                in_heredoc_body = True
            verb = None
            index += 2  # step over the delimiter word too
            continue

        # `> path` / `>> path` as separate tokens.
        if token in REDIRECT_TOKENS:
            if index + 1 < len(tokens) and is_sensitive(tokens[index + 1]):
                hits.append(tokens[index + 1])
            index += 2
            continue

        # `>path` / `2>>path` with no space.
        glued = re.match(r"^&?\d?>>?\|?(.+)$", token)
        if glued and is_sensitive(glued.group(1)):
            hits.append(glued.group(1))
            index += 1
            continue

        if in_heredoc_body:
            index += 1
            continue

        if verb is None:
            candidate = os.path.basename(token)
            verb = None if candidate in WRAPPERS else candidate
            pattern_arg_pending = verb in PATTERN_FIRST
            index += 1
            continue

        # `dd of=path`
        if token.startswith("of=") and is_sensitive(token[3:]):
            hits.append(token[3:])
            index += 1
            continue

        if token.startswith("-"):
            index += 1
            continue

        if pattern_arg_pending:
            pattern_arg_pending = False
            index += 1
            continue

        if is_sensitive(token) and (
            verb in WRITERS or verb in READERS or verb in EITHER or verb == "git"
        ):
            hits.append(token)

        index += 1

    return hits


def main():
    try:
        payload = json.load(sys.stdin)
        tool_name = payload.get("tool_name", "") or ""
        tool_input = payload.get("tool_input") or {}
        if not isinstance(tool_input, dict):
            raise ValueError("tool_input is not an object")
    except Exception as error:  # noqa: BLE001 - any parse failure must block
        sys.stderr.write(
            "BLOCKED: guard-secrets could not read its input (%s), so it cannot "
            "prove this call is safe. Failing closed.\n" % error
        )
        return 2

    offenders = []

    file_path = tool_input.get("file_path") or ""
    if isinstance(file_path, str) and file_path and is_sensitive(file_path):
        offenders.append(file_path)

    command = tool_input.get("command") or ""
    if isinstance(command, str) and command:
        offenders.extend(offending_paths_in_command(command))

    if not offenders:
        return 0

    unique = sorted(set(offenders))
    sys.stderr.write(
        "BLOCKED (%s): %s holds secrets or signing material.\n"
        % (tool_name or "unknown tool", ", ".join(unique))
    )
    sys.stderr.write(
        "Create or read it by hand outside the agent, keep it gitignored, and inject "
        "values at build time. Do not weaken this hook to get past it.\n"
    )
    return 2


if __name__ == "__main__":
    sys.exit(main())
