plugins {
    id("java")
}

// Doc comments are Markdown (JEP 467), where an unresolvable reference degrades
// silently: `[Foo#bar]` that resolves to nothing renders as the literal text
// "[Foo#bar]" rather than failing. Only javadoc's own reference check catches
// that, so it runs as part of `check` instead of never.
tasks.withType<Javadoc>().configureEach {
    // Avaje's annotation processor generates the *Module classes that
    // module-info `provides`; without them on the source path javadoc cannot
    // resolve module-info and stops before checking anything. Wired as a
    // provider so the generating compile task is a dependency.
    source(tasks.named<JavaCompile>("compileJava").flatMap { it.options.generatedSourceOutputDirectory })

    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions).apply {
        // Check every comment, not just the public API: most of this codebase's
        // doc comments — and most of its cross-references — sit on
        // package-private classes and private methods, which the default
        // (protected) visibility would skip entirely.
        memberLevel = JavadocMemberLevel.PRIVATE

        // References only. The codebase does not document @param/@return
        // exhaustively and does not intend to, so the other doclint groups
        // would be noise.
        addStringOption("Xdoclint:reference", "-quiet")
    }
}

tasks.named("check") {
    dependsOn(tasks.withType<Javadoc>())
}
