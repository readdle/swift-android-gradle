#!/usr/bin/env python

from utils import *


def copy_prebuilt(src, dst):
    copytree(os.path.join(src, "libs", BuildConfig.abi(), "*"), dst)
    copytree(os.path.join(src, "include", "*"), dst)


def copy_prebuilt_task(module):
    dst = Dirs.build_dir()
    
    mkdirs(dst)
    copy_prebuilt(module, dst)


def run():
    traverse_dependencies(copy_prebuilt_task)


if __name__ == "__main__":
    run()
