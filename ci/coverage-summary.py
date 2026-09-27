#!/usr/bin/env python3
"""Print the source files with the most uncovered statements from a scoverage XML report."""
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict

LIMIT = 25

counts = defaultdict(lambda: [0, 0])  # file -> [statements, invoked]
for statement in ET.parse(sys.argv[1]).getroot().iter("statement"):
    if statement.get("ignored") == "true":
        continue
    entry = counts[statement.get("source", "?").split("/src/main/scala/")[-1]]
    entry[0] += 1
    entry[1] += statement.get("invocation-count", "0") != "0"

rows = sorted(counts.items(), key=lambda item: item[1][1] - item[1][0])
print(f"{'uncovered':>9} {'total':>6} {'rate':>6}  file")
for name, (total, invoked) in rows[:LIMIT]:
    print(f"{total - invoked:>9} {total:>6} {100 * invoked / total:>5.1f}%  {name}")
