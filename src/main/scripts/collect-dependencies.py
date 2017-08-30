#!/usr/bin/env python

from __future__ import print_function

import os
import subprocess
import json
import glob
import platform

def traverse(root_node, func):
    def _traverse(node, func, is_root = False):
        children = node["dependencies"]
        for sub_node in children:
            _traverse(sub_node, func)

        if not is_root:
            func(node["path"])

    _traverse(root_node, func, True)

def copytree(src, dst, symlinks=False, ignore=None):
    src_files = glob.glob(src)

    if len(src_files) == 0:
        return

    subprocess.call(["rsync", "-r"] + src_files + [dst])


def copy_prebuilt(src, dst):
    copytree(os.path.join(src, "libs", "*"), dst)
    copytree(os.path.join(src, "include", "*"), dst)


print(subprocess.check_output(['swift', 'package', 'resolve']))

json_output = subprocess.check_output(["swift", "package", "show-dependencies", "--format", "json"])
root_node = json.loads(json_output)

root_path = root_node["path"]

if platform.system() == "Darwin":
    target = "x86_64-apple-macosx10.10"
else:
    target = "x86_64-unknown-linux"

def copy_prebuilt_task(module):
    dst = os.path.join(root_path, ".build", target, "debug")

    if not os.path.exists(dst):
        os.makedirs(dst)

    copy_prebuilt(module, dst)


traverse(root_node, copy_prebuilt_task)
