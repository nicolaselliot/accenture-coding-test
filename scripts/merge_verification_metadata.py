#!/usr/bin/env python3
"""Merge per-host Gradle dependency-verification metadata into one file.

Dependency verification is not host-independent for a Kotlin Multiplatform build: the metadata
generated on macOS pins `kotlin-native-prebuilt-*-macos-aarch64` and the macOS Skiko binaries,
while a Linux runner resolves the Linux equivalents. A file generated on one machine is therefore
red on the other two. See docs/adr/0004.

This unions the `<component>` entries from several generated files so the committed metadata covers
every host the CI matrix runs on. Components are keyed by (group, name, version) and their
`<artifact>` children are unioned by name, so a component that appears on two hosts keeps the
checksums from both.

Usage:
    merge_verification_metadata.py -o gradle/verification-metadata.xml part1.xml part2.xml ...

Exits non-zero if two hosts report different checksums for the same artifact, which would mean the
same coordinate resolved to different bytes and is a supply-chain signal, not a merge conflict.
"""

from __future__ import annotations

import argparse
import sys
import xml.etree.ElementTree as ET

NAMESPACE = "https://schema.gradle.org/dependency-verification"
ET.register_namespace("", NAMESPACE)


def qualified(tag: str) -> str:
    return f"{{{NAMESPACE}}}{tag}"


def artifact_checksums(artifact: ET.Element) -> dict[str, str]:
    """Checksum algorithm -> value, for one <artifact>."""
    return {
        child.tag.split("}")[-1]: child.get("value", "")
        for child in artifact
    }


def merge(paths: list[str]) -> tuple[ET.ElementTree, list[str]]:
    merged_tree = ET.parse(paths[0])
    merged_root = merged_tree.getroot()

    components_parent = merged_root.find(qualified("components"))
    if components_parent is None:
        components_parent = ET.SubElement(merged_root, qualified("components"))

    index: dict[tuple[str, str, str], ET.Element] = {}
    for component in components_parent.findall(qualified("component")):
        key = (component.get("group", ""), component.get("name", ""), component.get("version", ""))
        index[key] = component

    conflicts: list[str] = []

    for path in paths[1:]:
        root = ET.parse(path).getroot()
        incoming_parent = root.find(qualified("components"))
        if incoming_parent is None:
            continue

        for component in incoming_parent.findall(qualified("component")):
            key = (component.get("group", ""), component.get("name", ""), component.get("version", ""))
            existing = index.get(key)

            if existing is None:
                components_parent.append(component)
                index[key] = component
                continue

            existing_artifacts = {
                artifact.get("name", ""): artifact
                for artifact in existing.findall(qualified("artifact"))
            }

            for artifact in component.findall(qualified("artifact")):
                name = artifact.get("name", "")
                if name not in existing_artifacts:
                    existing.append(artifact)
                    existing_artifacts[name] = artifact
                    continue

                # Same coordinate, same artifact, two hosts. The bytes must agree.
                mine = artifact_checksums(existing_artifacts[name])
                theirs = artifact_checksums(artifact)
                for algorithm, value in theirs.items():
                    if algorithm in mine and mine[algorithm] != value:
                        conflicts.append(
                            f"{key[0]}:{key[1]}:{key[2]} {name} {algorithm}: "
                            f"{mine[algorithm]} != {value}"
                        )

    # Deterministic order, so the committed file does not churn by whichever host finished first.
    ordered = sorted(
        components_parent.findall(qualified("component")),
        key=lambda c: (c.get("group", ""), c.get("name", ""), c.get("version", "")),
    )
    for component in list(components_parent):
        components_parent.remove(component)
    for component in ordered:
        components_parent.append(component)

    return merged_tree, conflicts


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("inputs", nargs="+", help="Generated verification-metadata.xml files")
    parser.add_argument("-o", "--output", required=True, help="Where to write the merged file")
    args = parser.parse_args()

    tree, conflicts = merge(args.inputs)

    if conflicts:
        print("Checksum conflict between hosts — the same artifact resolved to different bytes:",
              file=sys.stderr)
        for conflict in conflicts:
            print(f"  {conflict}", file=sys.stderr)
        return 1

    ET.indent(tree, space="   ")
    tree.write(args.output, encoding="UTF-8", xml_declaration=True)

    count = len(tree.getroot().find(qualified("components")).findall(qualified("component")))
    print(f"Merged {len(args.inputs)} files into {args.output}: {count} components")
    return 0


if __name__ == "__main__":
    sys.exit(main())
