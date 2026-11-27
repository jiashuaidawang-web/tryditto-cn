#!/usr/bin/env python3
"""Run ProfileLiveEvalRunner on the server without importing the production env file.

Python 3.6. Reads only the three model fields needed for the selected role.
The child process receives those fields as the primary model and never receives
fallback, database, or application secrets. Nothing from the env file is printed.
"""
import os
import sys
from urllib.parse import urlparse


def parse_selected(path):
    wanted = {
        "LLM_PRIMARY_BASE_URL",
        "LLM_PRIMARY_API_KEY",
        "LLM_PRIMARY_MODEL",
        "LLM_FALLBACK_BASE_URL",
        "LLM_FALLBACK_API_KEY",
        "LLM_FALLBACK_MODEL",
    }
    found = {}
    with open(path, "r") as handle:
        for line in handle:
            text = line.strip()
            if not text or text.startswith("#") or "=" not in text:
                continue
            key, value = text.split("=", 1)
            key = key.strip()
            if key.startswith("export "):
                key = key[7:].strip()
            value = value.strip()
            if len(value) >= 2 and value[0] == value[-1] and value[0] in ("'", '"'):
                value = value[1:-1]
            if key in wanted:
                found[key] = value
    return found


def role_fields(role):
    if role == "longcat":
        return ("LLM_PRIMARY_BASE_URL", "LLM_PRIMARY_API_KEY", "LLM_PRIMARY_MODEL")
    if role == "deepseek":
        return ("LLM_FALLBACK_BASE_URL", "LLM_FALLBACK_API_KEY", "LLM_FALLBACK_MODEL")
    sys.stderr.write("role must be longcat or deepseek\n")
    return None


def leak_text(text, secrets):
    for secret in secrets:
        if secret and secret in text:
            return True
    return False


def secrets_of(values):
    return [values.get("LLM_PRIMARY_API_KEY", ""), values.get("LLM_FALLBACK_API_KEY", "")]


def main():
    if len(sys.argv) != 3:
        sys.stderr.write("usage: live-eval-remote.py longcat|deepseek preflight|smoke|full|scan\n")
        return 2
    role, mode = sys.argv[1], sys.argv[2]
    fields = role_fields(role)
    if fields is None:
        return 2
    root = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
    values = parse_selected("/opt/aiditto/.env")
    if mode == "scan":
        return scan(root, secrets_of(values))
    missing = [key for key in fields if not values.get(key, "").strip()]
    if missing:
        sys.stdout.write("BLOCKED missing %s\n" % ",".join(missing))
        return 2
    model = values[fields[2]].strip()
    host = urlparse(values[fields[0]].strip()).hostname or "unparsed"
    sys.stdout.write("config role=%s model=%s host=%s key=present fallback=cleared\n" % (role, model, host))
    report = os.path.join(root, "reports", role)
    logs = os.path.join(root, "logs")
    os.makedirs(report, 0o700, exist_ok=True)
    os.makedirs(logs, 0o700, exist_ok=True)
    os.chmod(report, 0o700)
    os.chmod(logs, 0o700)
    clean = {
        "PATH": os.environ.get("PATH", "/usr/bin:/bin"),
        "HOME": os.environ.get("HOME", "/root"),
        "LANG": "C.UTF-8",
        "LLM_MODE": "live",
        "LLM_PRIMARY_BASE_URL": values[fields[0]].strip(),
        "LLM_PRIMARY_API_KEY": values[fields[1]].strip(),
        "LLM_PRIMARY_MODEL": model,
        "LLM_TIMEOUT_MS": "45000",
        "LIVE_EVAL_OUT": "/eval/reports/" + role,
    }
    log_path = os.path.join(logs, "%s-%s.log" % (role, mode))
    cmd = [
        "docker", "run", "--rm",
        "--memory", "800m",
        "--pids-limit", "128",
        "-v", root + ":/eval",
        "-w", "/eval",
        "-e", "LLM_MODE",
        "-e", "LLM_PRIMARY_BASE_URL",
        "-e", "LLM_PRIMARY_API_KEY",
        "-e", "LLM_PRIMARY_MODEL",
        "-e", "LLM_TIMEOUT_MS",
        "-e", "LIVE_EVAL_OUT",
        "eclipse-temurin:17-jre",
        "java", "-Xmx256m", "-cp", "lib/*", "cn.aiditto.relationship.ProfileLiveEvalRunner", mode,
    ]
    import subprocess
    with open(log_path, "w") as log:
        code = subprocess.call(cmd, env=clean, stdout=log, stderr=subprocess.STDOUT)
    os.chmod(log_path, 0o600)
    lock_tree(report)
    text = open(log_path, "r", errors="replace").read()
    if leak_text(text, secrets_of(values)):
        sys.stdout.write("LOG_SUPPRESSED\nexit=%d\n" % code)
        return code if code else 1
    sys.stdout.write(text)
    if not text.endswith("\n"):
        sys.stdout.write("\n")
    sys.stdout.write("exit=%d\n" % code)
    return code


def scan(root, secrets):
    leaked = False
    for dirpath, _, files in os.walk(os.path.join(root, "reports")):
        for name in files:
            path = os.path.join(dirpath, name)
            text = open(path, "r", errors="replace").read()
            if leak_text(text, secrets):
                leaked = True
                sys.stdout.write("LEAK %s\n" % os.path.relpath(path, root))
    for dirpath, _, files in os.walk(os.path.join(root, "logs")):
        for name in files:
            path = os.path.join(dirpath, name)
            text = open(path, "r", errors="replace").read()
            if leak_text(text, secrets):
                leaked = True
                sys.stdout.write("LEAK %s\n" % os.path.relpath(path, root))
    if leaked:
        return 1
    sys.stdout.write("CLEAN\n")
    return 0


def lock_tree(path):
    if not os.path.isdir(path):
        return
    for dirpath, dirs, files in os.walk(path):
        os.chmod(dirpath, 0o700)
        for name in files:
            os.chmod(os.path.join(dirpath, name), 0o600)


if __name__ == "__main__":
    sys.exit(main())
