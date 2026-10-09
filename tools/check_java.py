#!/usr/bin/env python3
"""
Pre-push gate: catches the errors CI died on (broken class structure, bad braces, missing imports we own).
Runs javac on the mod sources without the Minecraft classpath, then ignores the errors that are only
missing-dependency noise. A parse/structure error fails. Usage: check_java.py <files...>
"""
import re, subprocess, sys, tempfile

files = sys.argv[1:]
if not files:
    # default: every .java changed vs the last pushed main (or HEAD), plus untracked ones - never a silent no-op
    base = subprocess.run(["git", "merge-base", "HEAD", "origin/main"], capture_output=True, text=True).stdout.strip() or "HEAD"
    out = subprocess.run(["git", "diff", "--name-only", base, "--"], capture_output=True, text=True).stdout
    out += subprocess.run(["git", "ls-files", "--others", "--exclude-standard"], capture_output=True, text=True).stdout
    import os
    files = sorted({f for f in out.split() if f.endswith(".java") and os.path.exists(f)})
    if not files:
        print("no changed .java files to check")
        sys.exit(0)
    print("checking changed files vs", base[:8] + ":", ", ".join(os.path.basename(f) for f in files))
with tempfile.TemporaryDirectory() as d:
    r = subprocess.run(["javac", "--release", "17", "-nowarn", "-Xmaxerrs", "5000", "-proc:none", "-d", d] + files,
                       capture_output=True, text=True)
noise = re.compile(r"(cannot find symbol|package [\w.]+ (does not exist|is not visible)|"
                   r"method does not override or implement|符号)|location:|symbol:|  (required|found):|\^|^import|"
                   r"error: cannot access|lambda expression not expected here|incompatible types|static import only from classes|name clash: |non-static variable super|class file for .* not found")
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
