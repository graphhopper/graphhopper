#!/usr/bin/env python3
r"""Capture an actual isolated server JVM and its artifact/configuration identity.

From cmd.exe after server startup:
    python scripts\record-server-runtime.py --name monaco --pid 12345 --log ..\audit\server-monaco.log
    python scripts\record-server-runtime.py --name manchester --pid 12346 --log ..\audit\server-manchester.log
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]


def sha(path, byte_limit=None):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        remaining = byte_limit
        while remaining is None or remaining > 0:
            block = stream.read(1024 * 1024 if remaining is None else min(1024 * 1024, remaining))
            if not block:
                break
            digest.update(block)
            if remaining is not None:
                remaining -= len(block)
        if remaining is not None and remaining:
            raise ValueError("Captured file became shorter while hashing")
    return digest.hexdigest()


def jcmd_path():
    candidates = []
    java_home = os.environ.get("JAVA_HOME")
    if java_home:
        candidates.append(Path(java_home.strip('"')) / "bin/jcmd.exe")
    found = shutil.which("jcmd")
    if found:
        candidates.append(Path(found))
    java = shutil.which("java")
    if java:
        candidates.append(Path(java).resolve().parent / "jcmd.exe")
        # Oracle's javapath launcher can hide the actual JDK bin directory.
        properties = subprocess.run([java, "-XshowSettings:properties", "-version"], capture_output=True, text=True)
        for line in (properties.stdout + "\n" + properties.stderr).splitlines():
            match = re.match(r"\s*java\.home\s*=\s*(.+?)\s*$", line)
            if match:
                candidates.append(Path(match.group(1)) / "bin/jcmd.exe")
    for candidate in candidates:
        if candidate.is_file():
            return candidate.resolve()
    raise FileNotFoundError("Cannot locate JDK jcmd via JAVA_HOME, PATH or java.home: " + ", ".join(map(str, candidates)))


def capture(jcmd, pid, operation):
    args = [str(jcmd), str(pid), operation]
    result = subprocess.run(args, capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError(f"{operation} exited {result.returncode}: {result.stderr or result.stdout}")
    return {"argv": args, "exit_code": result.returncode, "stdout": result.stdout, "stderr": result.stderr}


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--name", required=True, choices=("monaco", "manchester"))
    parser.add_argument("--pid", required=True, type=int)
    parser.add_argument("--log", required=True, type=Path)
    args = parser.parse_args()
    if args.pid <= 0:
        parser.error("pid must be positive")
    config = ROOT / ("docs/matrix/config-matrix.yml" if args.name == "monaco" else "docs/matrix/config-manchester.yml")
    jar = ROOT / "web/target/graphhopper-web-11.0-SNAPSHOT.jar"
    log = (args.log if args.log.is_absolute() else ROOT / args.log).resolve(strict=True)
    if not log.is_relative_to(ROOT.parent):
        raise ValueError("The requested server log must belong to this workspace")
    config_text = config.read_text(encoding="utf-8")
    graph_match = re.search(r"^\s*graph\.location:\s*(.+?)\s*$", config_text, re.MULTILINE)
    ports = [int(value) for value in re.findall(r"^\s*port:\s*(\d+)\s*$", config_text, re.MULTILINE)]
    if not graph_match or len(ports) != 2:
        raise ValueError("Expected this project's two-connector example server configuration")
    jcmd = jcmd_path()
    command_line = capture(jcmd, args.pid, "VM.command_line")
    flags = capture(jcmd, args.pid, "VM.flags")
    command_text = command_line["stdout"].replace("\\", "/").lower()
    if jar.name.lower() not in command_text or config.name.lower() not in command_text:
        raise ValueError("The captured JVM command line does not identify the requested matrix JAR/configuration")
    if re.search(r"-Ddw\.(?:graphhopper\.graph\.location|server\.)", command_line["stdout"], re.IGNORECASE):
        raise ValueError("Graph directory/server connector overrides need an explicit runtime configuration record")
    audit = ROOT.parent / "audit"
    audit.mkdir(exist_ok=True)
    texts = {}
    for label, value in (("jvm-command-line", command_line), ("jvm-flags", flags)):
        path = audit / ("server-" + args.name + "-" + label + ".txt")
        path.write_text(value["stdout"] + ("\nSTDERR:\n" + value["stderr"] if value["stderr"] else ""), encoding="utf-8")
        texts[label] = {"path": str(path), "sha256": sha(path), "command": value["argv"], "exit_code": value["exit_code"]}
    graph_directory = graph_match.group(1).strip('"\'')
    log_size_at_capture = log.stat().st_size
    record = {"captured_utc": datetime.now(timezone.utc).isoformat(), "pid": args.pid, "name": args.name,
              "repository_root": str(ROOT), "jcmd_path": str(jcmd),
              "configuration_path": str(config), "configuration_sha256": sha(config),
              "configuration_hash_scope": "File bytes at capture; JVM command line independently identifies the selected config path.",
              "artifact_path": str(jar), "artifact_size_bytes": jar.stat().st_size, "artifact_sha256": sha(jar),
              "graph_directory_as_configured": graph_directory, "graph_directory_resolved": str((ROOT / graph_directory).resolve()),
              "application_port": ports[0], "admin_port": ports[1],
              "jcmd_command_line": command_line, "jcmd_flags": flags, "captured_text_files": texts,
              "log": {"path": str(log), "size_bytes_at_capture": log_size_at_capture, "sha256_at_capture": sha(log, log_size_at_capture),
                      "hash_scope": "Exactly the first size_bytes_at_capture bytes; a running server log can grow later."}}
    destination = ROOT / "docs/matrix/runtime-manifest.json"
    manifest = json.loads(destination.read_text(encoding="utf-8")) if destination.exists() else {"servers": {}}
    manifest.setdefault("servers", {})[args.name] = record
    manifest["updated_utc"] = record["captured_utc"]
    destination.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"manifest": str(destination), "server": args.name, "pid": args.pid,
                      "artifact_sha256": record["artifact_sha256"], "configuration_sha256": record["configuration_sha256"]}))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, RuntimeError) as error:
        print(f"Runtime capture failed: {error}", file=sys.stderr)
        raise SystemExit(1)
