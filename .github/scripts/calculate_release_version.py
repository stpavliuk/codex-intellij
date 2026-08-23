#!/usr/bin/env python3

import os
import re
import subprocess
from pathlib import Path


TAG_PATTERN = re.compile(r"v(\d+)\.(\d+)\.(\d+)")


def main() -> None:
    tags = subprocess.run(
        ["git", "tag", "--list", "--sort=-v:refname"],
        check=True,
        capture_output=True,
        text=True,
    ).stdout.splitlines()
    latest_match = next(
        (match for tag in tags if (match := TAG_PATTERN.fullmatch(tag))),
        None,
    )

    if latest_match is None:
        latest_tag = None
        major, minor, patch = 0, 1, 0
    else:
        latest_tag = latest_match.group(0)
        major, minor, patch = map(int, latest_match.groups())
        if os.environ["BUMP_MAJOR"] == "true":
            major, minor, patch = major + 1, 0, 0
        elif os.environ["BUMP_MINOR"] == "true":
            minor, patch = minor + 1, 0
        else:
            patch += 1

    version = f"{major}.{minor}.{patch}"
    print(f"Releasing v{version} after {latest_tag or 'no previous release'}")
    with Path(os.environ["GITHUB_OUTPUT"]).open("a", encoding="utf-8") as output:
        print(f"version={version}", file=output)


if __name__ == "__main__":
    main()
