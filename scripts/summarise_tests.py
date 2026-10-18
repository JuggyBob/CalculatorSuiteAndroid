#!/usr/bin/env python3
"""Summarise JUnit XML results into a Markdown table for the CI job summary.

Reads every ``*.xml`` file Gradle writes to
``app/build/test-results/testDebugUnitTest`` and prints a Markdown fragment.
Exits non-zero if the directory is missing, so CI reports that honestly
rather than showing a misleading empty table.
"""
import glob
import os
import sys
import xml.etree.ElementTree as ET

RESULTS = os.path.join("app", "build", "test-results", "testDebugUnitTest")

if not os.path.isdir(RESULTS):
    print("_No unit test results were produced._")
    sys.exit(0)

files = sorted(glob.glob(os.path.join(RESULTS, "*.xml")))
if not files:
    print("_No unit test result files were found._")
    sys.exit(0)

suites = total = failures = errors = skipped = 0
broken = []

for path in files:
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError:
        broken.append(os.path.basename(path))
        continue
    suites += 1
    total += int(root.get("tests", 0))
    failures += int(root.get("failures", 0))
    errors += int(root.get("errors", 0))
    skipped += int(root.get("skipped", 0))

print("### Unit tests")
print()
print("| | |")
print("|---|---|")
print("| Test suites | %d |" % suites)
print("| Tests | %d |" % total)
print("| Failures | %d |" % failures)
print("| Errors | %d |" % errors)
if skipped:
    print("| Skipped | %d |" % skipped)
print()

if broken:
    print("_Unparseable result files: %s_" % ", ".join(broken))
    print()

if failures or errors:
    print("> **Some tests failed.** See the uploaded `unit-test-report` artifact.")
else:
    print("All %d tests passed." % total)
