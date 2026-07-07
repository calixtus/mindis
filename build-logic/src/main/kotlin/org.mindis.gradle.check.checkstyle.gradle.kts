plugins {
    id("checkstyle")
}

checkstyle {
    toolVersion = "13.7.0"
    configFile = File(rootDir, "config/checkstyle/checkstyle.xml")
}

tasks.withType<Checkstyle>().configureEach {
    reports {
        xml.required = false
        html.required = true
    }
    // Checkstyle cannot parse module-info.java files.
    exclude("module-info.java")
}
