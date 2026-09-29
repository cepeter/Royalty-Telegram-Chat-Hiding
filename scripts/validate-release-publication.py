#!/usr/bin/env python3
"""Validate a staged tag release against current published releases under the publication lock."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile


SIGNER = (Path(__file__).resolve().parents[1] / "release-signing-cert.sha256").read_text().strip()
BOOTSTRAP = ("v3.0.5", 17, "royalty-v3.0.5.apk")
TAG = re.compile(r"v\d+\.\d+\.\d+\Z")
SHA = re.compile(r"[0-9a-f]{64}\Z")


def fail(message):
    raise ValueError(message)


def version(path):
    values = {}
    for line in path.read_text().splitlines():
        match = re.fullmatch(r"(versionName|versionCode)=([^=]+)", line)
        if not match or match[1] in values:
            fail("Invalid version.properties")
        values[match[1]] = match[2]
    if set(values) != {"versionName", "versionCode"} or not TAG.fullmatch("v" + values["versionName"]):
        fail("Invalid version.properties")
    if not re.fullmatch(r"[1-9]\d*", values["versionCode"]):
        fail("Invalid version.properties")
    return values["versionName"], int(values["versionCode"])


def metadata(path, tag, code=None):
    data = json.loads(path.read_text())
    if not isinstance(data, dict) or set(data) != {"tag", "versionName", "versionCode", "signerSha256", "apkSha256"}:
        fail(f"Invalid release metadata: {path}")
    if data["tag"] != tag or data["versionName"] != tag[1:] or type(data["versionCode"]) is not int or data["versionCode"] < 1:
        fail(f"Release metadata does not match tag: {tag}")
    if code is not None and data["versionCode"] != code:
        fail("Staged release code does not match version.properties")
    if data["signerSha256"] != SIGNER or not isinstance(data["apkSha256"], str) or not SHA.fullmatch(data["apkSha256"]):
        fail(f"Invalid signer or checksum metadata: {tag}")
    return data


def published_releases(repo):
    response = subprocess.run(["gh", "api", "--paginate", f"repos/{repo}/releases?per_page=100"],
                              capture_output=True, text=True, check=True).stdout
    decoder = json.JSONDecoder()
    releases = []
    position = 0
    while position < len(response):
        while position < len(response) and response[position].isspace():
            position += 1
        if position == len(response):
            break
        page, position = decoder.raw_decode(response, position)
        if not isinstance(page, list):
            fail("Malformed release API response")
        releases.extend(page)
    if not releases:
        fail("No published releases; legacy bootstrap cannot be confirmed")
    return releases


def validate(staged, version_file):
    name, code = version(version_file)
    tag = "v" + name
    if os.environ["GITHUB_REF_NAME"] != tag:
        fail("Publication tag does not match version.properties")
    apk = staged / f"royalty-{tag}.apk"
    data = metadata(staged / f"royalty-{tag}.apk.release.json", tag, code)
    actual = hashlib.sha256(apk.read_bytes()).hexdigest()
    if actual != data["apkSha256"]:
        fail("Staged APK differs from verified metadata")
    if (staged / f"royalty-{tag}.apk.sha256").read_text() != f"{actual}  royalty-{tag}.apk\n":
        fail("Staged APK checksum manifest mismatch")
    highest = None
    bootstrap_found = False
    for release in published_releases(os.environ["GITHUB_REPOSITORY"]):
        if not isinstance(release, dict) or not isinstance(release.get("tag_name"), str) or type(release.get("draft")) is not bool or not isinstance(release.get("assets"), list):
            fail("Malformed release API entry")
        prior_tag = release["tag_name"]
        if release["draft"] or not TAG.fullmatch(prior_tag):
            continue
        if prior_tag == tag:
            fail(f"Release tag is already published: {tag}")
        if tuple(map(int, prior_tag[1:].split("."))) < (3, 0, 5):
            # v3.0.5 is the verified boundary for release metadata.
            continue
        names = [asset.get("name") for asset in release["assets"] if isinstance(asset, dict)]
        apk_name = f"royalty-{prior_tag}.apk"
        if apk_name not in names:
            fail(f"Unrecognized published release: {prior_tag}")
        meta_name = f"{apk_name}.release.json"
        if prior_tag == BOOTSTRAP[0]:
            bootstrap_found = True
        if prior_tag == BOOTSTRAP[0] and meta_name not in names:
            prior_code = BOOTSTRAP[1]
        else:
            if meta_name not in names:
                fail(f"Published release has no validated metadata: {prior_tag}")
            with tempfile.TemporaryDirectory() as directory:
                subprocess.run(["gh", "release", "download", prior_tag, "--repo", os.environ["GITHUB_REPOSITORY"],
                                "--pattern", meta_name, "--dir", directory], check=True, capture_output=True, text=True)
                prior_code = metadata(Path(directory) / meta_name, prior_tag)["versionCode"]
            if prior_tag == BOOTSTRAP[0] and prior_code != BOOTSTRAP[1]:
                fail("Published bootstrap metadata contradicts verified version code")
        highest = prior_code if highest is None else max(highest, prior_code)
    if not bootstrap_found:
        fail("Verified v3.0.5 bootstrap release is missing")
    if highest is None or code <= highest:
        fail(f"Release code {code} must exceed latest published code {highest}")
    print(f"Validated {tag} code {code} above published code {highest}")


if __name__ == "__main__":
    try:
        validate(Path(sys.argv[1]), Path(sys.argv[2]))
    except (OSError, ValueError, KeyError, IndexError, json.JSONDecodeError, subprocess.CalledProcessError) as error:
        sys.exit(f"Publication validation failed: {error}")
