from __future__ import annotations

import os
import subprocess

PROTECTED_BRANCHES = {"main", "master", "develop"}


def current_branch() -> str:
    env_branch = os.environ.get("PROTECTED_BRANCH_GUARD_BRANCH")
    if env_branch:
        return env_branch

    result = subprocess.run(
        ["git", "branch", "--show-current"],
        check=False,
        capture_output=True,
        text=True,
    )
    return result.stdout.strip()


def is_protected_branch(branch: str) -> bool:
    return branch in PROTECTED_BRANCHES
