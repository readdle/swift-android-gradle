#!/usr/bin/env python
from __future__ import print_function

from os import getenv
from os.path import expanduser

from utils import *

SWIFT_INSTALL = getenv("SWIFT_INSTALL")


def extract_tests_package(package):
    return package["name"] + "PackageTests.xctest"


def push(dst, name, skip_push_stdlib, skip_push_external):
    from os.path import join

    adb_shell(["mkdir", "-p", dst])

    if not skip_push_stdlib:
        adb_push(dst, glob(join(SWIFT_INSTALL, "usr/lib/swift/android", "*.so")))

    if not skip_push_external:
        adb_push(dst, glob(join(Dirs.external_libs_dir(), "*.so")))

    adb_push(dst, glob(join(Dirs.build_dir(), "*.so")))
    adb_push(dst, [join(Dirs.build_dir(), name)])


def exec_tests(folder, name, args):
    ld_path = "LD_LIBRARY_PATH=" + folder
    test_path = folder + "/" + name

    return adb_shell([ld_path, test_path] + args)


def run(skip_build=False, skip_push=False,
        skip_push_stdlib=False, skip_push_external=False,
        build_args=None, test_args=None):

    if test_args is None:
        test_args = []
    if build_args is None:
        build_args = []

    skip_build = skip_build or skip_push

    if not skip_build:
        sh_checked(
            [
                expanduser("~/.gradle/scripts/swift-build.sh"), 
                "--build-tests"
            ] + build_args
        )

    package = get_package_description()
    name = extract_tests_package(package)
    folder = "/data/local/tmp/" + name.split(".")[0]

    if not skip_push:
        push(folder, name, skip_push_stdlib, skip_push_external)

    return exec_tests(folder, name, test_args)


if __name__ == "__main__":
    from argparse import ArgumentParser

    parser = ArgumentParser()

    parser.add_argument(
        "--skip-build", 
        dest="skip_build",
        action="store_true",
        help="Skip rebuilding. Only deploy and run.", 
    )

    parser.add_argument(
        "-f", "--skip-push",
        dest="skip_push",
        action="store_true",
        default=False,
        help="Skip rebuilding and redeploying. Just run."
    )

    parser.add_argument(
        "--skip-push-stdlib",
        dest="skip_push_stdlib",
        action="store_true",
        default=False,
        help="Dont push externaly builded libraries"
    )

    parser.add_argument(
        "--skip-push-external",
        dest="skip_push_external",
        action="store_true",
        default=False,
        help="Dont push toolchain libraries"
    )

    parser.add_argument(
        "-Xbuild",
        dest="build_args",
        action="append", 
        default=[],
        help="Pass flag through to Swift PM"
    )

    parser.add_argument(
        "-Xtest",
        dest="test_args",
        action="append", 
        default=[],
        help="Pass flag through to XCTest"
    )

    args = parser.parse_args()

    run(args.skip_build, 
        args.skip_push, 
        args.skip_push_stdlib,
        args.skip_push_external,
        args.build_args, 
        args.test_args)
