package com.readdle.android.swift.gradle

import org.gradle.api.Project
import org.gradle.util.GUtil

class SwiftAndroidPluginExtension {
    static class SwiftFlags {
        private final List<String> extraBuildFlags = new ArrayList<>()
        private final List<String> extraInstallFlags = new ArrayList<>()
        private final Set<String> abiFilters = new HashSet<>()

        SwiftFlags extraBuildFlags(String... extraBuildFlags) {
            if (extraBuildFlags == null) {
                throw new IllegalArgumentException("extraBuildFlags == null!")
            }

            this.extraBuildFlags.addAll(Arrays.asList(extraBuildFlags))
            return this
        }

        SwiftFlags extraBuildFlags(Iterable<String> extraBuildFlags) {
            GUtil.addToCollection(this.extraBuildFlags, extraBuildFlags)
            return this
        }

        SwiftFlags setExtraBuildFlags(Iterable<String> extraBuildFlags) {
            this.extraBuildFlags.clear()
            GUtil.addToCollection(this.extraBuildFlags, extraBuildFlags)
            return this
        }

        List<String> getExtraBuildFlags() {
            List<String> args = new ArrayList<String>()
            for (String argument : extraBuildFlags) {
                args.add(argument)
            }

            return args
        }

        SwiftFlags extraInstallFlags(String... extraInstallFlags) {
            if (extraInstallFlags == null) {
                throw new IllegalArgumentException("extraInstallFlags == null!")
            }

            this.extraInstallFlags.addAll(Arrays.asList(extraInstallFlags))
            return this
        }

        SwiftFlags extraInstallFlags(Iterable<String> extraInstallFlags) {
            GUtil.addToCollection(this.extraInstallFlags, extraInstallFlags)
            return this
        }

        SwiftFlags setExtraInstallFlags(Iterable<String> extraInstallFlags) {
            this.extraInstallFlags.clear()
            GUtil.addToCollection(this.extraInstallFlags, extraInstallFlags)
            return this
        }

        List<String> getExtraInstallFlags() {
            List<String> args = new ArrayList<String>()
            for (String argument : extraInstallFlags) {
                args.add(argument)
            }

            return args
        }

        @Deprecated
        SwiftFlags abiFilters(String... abiFilters) {
            if (abiFilters == null) {
                throw new IllegalArgumentException("abiFilters == null!")
            }

            this.abiFilters.addAll(validateAbi(Arrays.asList(abiFilters)))
            return this
        }

        @Deprecated
        SwiftFlags abiFilters(Iterable<String> abiFilters) {
            GUtil.addToCollection(this.abiFilters, validateAbi(abiFilters))
            return this
        }

        @Deprecated
        SwiftFlags setAbiFilters(Iterable<String> abiFilters) {
            this.abiFilters.clear()
            GUtil.addToCollection(this.abiFilters, validateAbi(abiFilters))
            return this
        }

        Set<String> getAbiFilters() {
            return new HashSet<String>(abiFilters)
        }

        private static <T extends Iterable<? extends String>> T validateAbi(T arg) {
            for (String abi : arg) {
                if (!Arch.isValidAndroidAbi(abi)) {
                    throw new IllegalArgumentException("'${abi}' is not valid Android ABI")
                }
            }

            return arg
        }
    }

    private Project project
    SwiftFlags debug = new SwiftFlags()
    SwiftFlags release = new SwiftFlags()

    boolean cleanEnabled = true
    boolean usePackageClean = true
    boolean swiftLintEnabled = false
    boolean useKapt = false
    int apiLevel = 24

    SwiftAndroidPluginExtension(Project project) {
        this.project = project
    }

    SwiftFlags debug(Closure closure) {
        debug = new SwiftFlags()
        project.configure(debug, closure)
        return debug
    }

    SwiftFlags release(Closure closure) {
        release = new SwiftFlags()
        project.configure(release, closure)
        return release
    }
}
