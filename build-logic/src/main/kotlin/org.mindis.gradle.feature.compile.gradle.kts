plugins {
    id("java")
    id("checkstyle")
}

group = "org.mindis"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

checkstyle {
    toolVersion = "13.7.0"
    configFile = rootProject.file("config/checkstyle/checkstyle.xml")
}

// Checkstyle cannot parse module-info.java files.
tasks.withType<Checkstyle>().configureEach {
    exclude("module-info.java")
}
