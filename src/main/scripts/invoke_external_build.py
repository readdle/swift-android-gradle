#!/usr/bin/env python

from os import getenv

import collect_dependencies
from utils import *

SWIFT_INSTALL = getenv("SWIFT_INSTALL")


def make_env():
    swift_lib = os.path.join(SWIFT_INSTALL, "usr", "lib", "swift")
    external_build_dir = Dirs.external_build_dir()
    external_include_dir = Dirs.external_include_dir()

    pm_build_dir = Dirs.build_dir()

    mkdirs(swift_lib)
    mkdirs(external_build_dir)
    mkdirs(external_include_dir)
    mkdirs(pm_build_dir)

    return {
        "SWIFT_LIB": swift_lib,
        "SWIFT_PM_EXTERNAL_LIBS": external_build_dir,
        "SWIFT_PM_EXTERNAL_INCLUDE": external_include_dir,
        "SWIFT_PM_BUILD_DIR": pm_build_dir
    }


def get_script_path(base):
    return os.path.join(base, "build-android-swift", "invoke-external-build")


def is_executable(path):
    return os.path.isfile(path) and os.access(path, os.X_OK)


def invoke_external(path):
    script = get_script_path(path)

    if not is_executable(script):
        return

    sh_checked([script], env=make_env())


def run():
    collect_dependencies.run()
    traverse_dependencies(invoke_external, include_root=True)


if __name__ == "__main__":
    from argparse import ArgumentParser

    parser = ArgumentParser()

    parser.add_argument(
        "-c", "--configuration",
        default="debug",
        help="Build with configuration (debug|release) [default: debug]"
    )

    args = parser.parse_args()

    BuildConfig.setup(
        configuration=args.configuration
    )

    run()
