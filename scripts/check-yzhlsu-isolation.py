#!/usr/bin/env python3
"""Check source-level invariants for the yzhlSU control/data boundary."""

from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[1]

EXPECTED = {
    "uapi/supercall.h": ["0x595A484C", "0x53553031", "_IOR('Y'"],
    "kernel/Kbuild": [
        "KSU_MANAGER_PACKAGE := me.yzhl.su",
        "yzhlSU requires KSU_EXPECTED_SIZE",
        "yzhlSU requires KSU_EXPECTED_HASH",
    ],
    "kernel/supercall/supercall.c": ["[yzhlsu_driver]", "[yzhlsu_driver_su]"],
    "manager/app/src/main/cpp/ksu.cc": ["[yzhlsu_driver]"],
    "userspace/ksud/src/ksucalls.rs": [
        "anon_inode:[yzhlsu_driver]",
        "anon_inode:[yzhlsu_driver_su]",
    ],
    "userspace/ksud/src/defs.rs": [
        '"yzhlsu/"',
        '"yzhlsud"',
        'concatcp!(WORKING_DIR, "modules/")',
    ],
    "kernel/selinux/selinux.h": ['"yzhlsu"', '"yzhlsu_file"'],
    "manager/app/build.gradle.kts": ['"me.yzhl.su"', '"yzhlSU"'],
    ".github/workflows/ddk-lkm.yml": [
        "KSU_MANAGER_PACKAGE=me.yzhl.su",
        "KSU_EXPECTED_SIZE=$EXPECTED_SIZE",
        "KSU_EXPECTED_HASH=$EXPECTED_HASH",
    ],
    ".github/workflows/build-manager.yml": [
        "workflow_dispatch:",
        "secrets.KEYSTORE",
        "expected_size: ${{ needs.generate-key.outputs.expected_size }}",
    ],
    ".github/workflows/build-lkm.yml": ["android16-6.12", "build_x86_64: false"],
}

FORBIDDEN = {
    "kernel/supercall/supercall.c": ["[ksu_driver]", "[ksu_driver_su]"],
    "manager/app/src/main/cpp/ksu.cc": ["[ksu_driver]"],
    "userspace/ksud/src/ksucalls.rs": [
        "anon_inode:[ksu_driver]",
        "anon_inode:[ksu_driver_su]",
    ],
    "userspace/ksud/src/defs.rs": [
        'concatcp!(ADB_DIR, "ksu/")',
        'concatcp!(ADB_DIR, "ksud")',
        'concatcp!(ADB_DIR, "modules/")',
    ],
    "kernel/policy/allowlist.c": ['"/data/adb/ksu/.allowlist"'],
    "uapi/supercall.h": ["'K'"],
    "manager/app/src/main/AndroidManifest.xml": ['android:scheme="ksu"'],
    ".github/workflows/build-lkm.yml": [
        "android12-5.10",
        "android13-5.10",
        "android13-5.15",
        "android14-5.15",
        "android14-6.1",
        "android15-6.6",
        "android17-6.18",
    ],
}


def main() -> int:
    errors: list[str] = []

    for relative, needles in EXPECTED.items():
        text = (ROOT / relative).read_text(encoding="utf-8")
        for needle in needles:
            if needle not in text:
                errors.append(f"{relative}: missing {needle!r}")

    for relative, needles in FORBIDDEN.items():
        text = (ROOT / relative).read_text(encoding="utf-8")
        for needle in needles:
            if needle in text:
                errors.append(f"{relative}: retained upstream boundary {needle!r}")

    if errors:
        print("yzhlSU isolation check failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    print("yzhlSU isolation check passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
