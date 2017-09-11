#!/usr/bin/env python

from __future__ import print_function

import os
import sys
import subprocess
import json
import glob
import platform
from os.path import expanduser

SWIFT_INSTALL=os.getenv("SWIFT_INSTALL")

def check_return_code(code):
    if code != 0:
        sys.exit(code)

def sh_checked(cmd):
    check_return_code(subprocess.call(cmd))

def extract_tests_package(json):
    return json["name"] + "PackageTests.xctest"

def push(dst, name):
    adb_shell(["mkdir", "-p", dst])

    adb_push(dst, glob.glob(SWIFT_INSTALL + "/usr/lib/swift/android/*.so"))
    adb_push(dst, glob.glob(".build/debug/*.so"))
    adb_push(dst, [".build/debug/" + name])

def adb_push(dst, files):
    for f in files:
        sh_checked(["adb", "push", f, dst])

def adb_shell(args):
    return sh_checked(["adb", "shell"] + args)

def exec_tests(folder, name):
    ld_path = "LD_LIBRARY_PATH=" + folder
    test_path = folder + "/" + name

    return adb_shell([ld_path, test_path] + sys.argv[1:])


def run(json):
    sh_checked([expanduser("~/.gradle/scripts/swift-build.sh"), "--build-tests"])

    name = extract_tests_package(package_json)
    folder = "/data/local/tmp/" + name.split(".")[0]

    push(folder, name)
    return exec_tests(folder, name)

sh_checked(["swift", "package", "resolve"])

package_dump = subprocess.check_output(["swift", "package", "dump-package"])
package_json = json.loads(package_dump)

run(package_json)