#!/usr/bin/env python3
r"""Remove only owned PT test caches after the test JVM exits (stdlib only).

Run from cmd.exe after Maven finishes:
    python scripts\cleanup-pt-test-caches.py --dry-run
    python scripts\cleanup-pt-test-caches.py

The two exact historical fixture names and their UUID-suffixed replacements are
recognized only beneath this checkout's web/target. All resolved targets are
checked before deletion; graph-cache and arbitrary graph directories are excluded.
"""
import argparse
import json
from pathlib import Path
import re
import shutil


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    target = (root / "web/target").resolve()
    if not target.is_relative_to(root) or target != root / "web" / "target":
        raise ValueError("Generated target directory resolves outside the expected checkout path")
    name = re.compile(r"gtfs-(?:route-resource|trip-based-resource)-gh(?:-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})?")
    rows = []
    for candidate in sorted(target.iterdir()) if target.exists() else []:
        if not candidate.is_dir() or not name.fullmatch(candidate.name):
            continue
        resolved = candidate.resolve(strict=True)
        if candidate.is_symlink() or resolved != candidate.absolute() or resolved.parent != target or not resolved.is_relative_to(root):
            raise ValueError(f"Unsafe owned-cache target: {candidate}")
        row = {"path": str(resolved), "status": "WOULD_REMOVE" if args.dry_run else "REMOVED"}
        if not args.dry_run:
            try:
                shutil.rmtree(resolved)
            except OSError as exc:
                row.update(status="RETAINED", error=str(exc), note="Ensure the test JVM has exited and retry.")
        rows.append(row)
    print(json.dumps({"dry_run": args.dry_run, "owned_cache_results": rows}, indent=2))
    return 1 if any(row["status"] == "RETAINED" for row in rows) else 0


if __name__ == "__main__":
    raise SystemExit(main())
