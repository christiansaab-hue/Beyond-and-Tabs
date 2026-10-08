#!/usr/bin/env python3
"""
Pre-push gate: catches the errors CI died on (broken class structure, bad braces, missing imports we own).
Runs javac on the mod sources without the Minecraft classpath, then ignores the errors that are only
missing-dependency noise. A parse/structure error fails. Usage: check_java.py <files...>
"""
import re, subprocess, sys, tempfile

files = sys.argv[1:]
if not files:
    sys.exit(0)
with tempfile.TemporaryDirectory() as d:
    r = subprocess.run(["javac", "--release", "17", "-nowarn", "-Xmaxerrs", "5000", "-proc:none", "-d", d] + files,
                       capture_output=True, text=True)
noise = re.compile(r"(cannot find symbol|package [\w.]+ (does not exist|is not visible)|"
                   r"method does not override or implement|符号)|location:|symbol:|  (required|found):|\^|^import|"
                   r"error: cannot access|static import only from classes|name clash: |class file for .* not found")
bad = []
lines = r.stderr.splitlines()
for i, l in enumerate(lines):
    m = re.match(r"(.+\.java):(\d+): error: (.*)", l)
    if m and not noise.search(m.group(3)):
        bad.append(l)
for l in bad[:25]:
    print(l)
print(f"{len(files)} files checked, {len(bad)} structural errors")
sys.exit(1 if bad else 0)
