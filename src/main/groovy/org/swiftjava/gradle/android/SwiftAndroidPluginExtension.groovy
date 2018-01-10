package org.swiftjava.gradle.android

import org.gradle.api.Project
import org.gradle.util.GUtil

class SwiftAndroidPluginExtension {
    static class SwiftFlags {
        private final List<Object> extraBuildFlags = new ArrayList<>()
        private final List<Object> extraInstallFlags = new ArrayList<>()

        SwiftFlags extraBuildFlags(Object... extraBuildFlags) {
            if (extraBuildFlags == null) {
                throw new IllegalArgumentException("extraBuildFlags == null!")
            }

            this.extraBuildFlags.addAll(Arrays.asList(extraBuildFlags))
            return this
        }

        SwiftFlags extraBuildFlags(Iterable<?> extraBuildFlags) {
            GUtil.addToCollection(this.extraBuildFlags, extraBuildFlags)
            return this
        }

        SwiftFlags setExtraBuildFlags(List<String> extraBuildFlags) {
            this.extraBuildFlags.clear()
            this.extraBuildFlags.addAll(extraBuildFlags)
            return this
        }

        SwiftFlags setExtraBuildFlags(Iterable<?> extraBuildFlags) {
            this.extraBuildFlags.clear()
            GUtil.addToCollection(this.extraBuildFlags, extraBuildFlags)
            return this
        }

        List<String> getExtraBuildFlags() {
            List<String> args = new ArrayList<String>()
            for (Object argument : extraBuildFlags) {
                args.add(argument.toString())
            }

            return args
        }

        SwiftFlags extraInstallFlags(Object... extraInstallFlags) {
            if (extraInstallFlags == null) {
                throw new IllegalArgumentException("extraInstallFlags == null!")
            }

            this.extraInstallFlags.addAll(Arrays.asList(extraInstallFlags))
            return this
        }

        SwiftFlags extraInstallFlags(Iterable<?> extraInstallFlags) {
            GUtil.addToCollection(this.extraInstallFlags, extraInstallFlags)
            return this
        }

        SwiftFlags setExtraInstallFlags(List<String> extraInstallFlags) {
            this.extraInstallFlags.clear()
            this.extraInstallFlags.addAll(extraInstallFlags)
            return this
        }

        SwiftFlags setExtraInstallFlags(Iterable<?> extraInstallFlags) {
            this.extraInstallFlags.clear()
            GUtil.addToCollection(this.extraInstallFlags, extraInstallFlags)
            return this
        }

        List<String> getExtraInstallFlags() {
            List<String> args = new ArrayList<String>()
            for (Object argument : extraInstallFlags) {
                args.add(argument.toString())
            }

            return args
        }
    }


    private Project project
    SwiftFlags debug = new SwiftFlags()
    SwiftFlags release = new SwiftFlags()

    boolean cleanEnabled = true
    boolean usePackageClean = true

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
