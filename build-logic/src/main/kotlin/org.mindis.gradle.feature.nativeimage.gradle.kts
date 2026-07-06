// Exposes the GraalVM native-image plugin (M7). Applied only by the
// native-spike project; the shipped app stays on jpackage (PLAN.md M6/M7).
plugins {
    id("org.graalvm.buildtools.native")
}
