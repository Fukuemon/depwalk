plugins {
    java
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-autoconfigure:4.1.0")
    implementation("org.springframework.data:spring-data-commons:4.1.0")
    implementation("org.mybatis:mybatis:3.5.19")
    compileOnly("org.projectlombok:lombok:1.18.46")
    annotationProcessor("org.projectlombok:lombok:1.18.46")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(21)
    options.compilerArgs.add("-parameters")
}

tasks.register("writeDepwalkClasspath") {
    dependsOn(tasks.classes)
    val outputFile = layout.buildDirectory.file("depwalk-classpath.txt")
    outputs.file(outputFile)

    doLast {
        val resolvedEntries = sourceSets.main.get().output.classesDirs.files + configurations.runtimeClasspath.get().files
        resolvedEntries.forEach { entry ->
            require(entry.exists()) { "depwalk classpath entry does not exist: ${entry.absolutePath}" }
        }
        val entries = resolvedEntries
            .map { it.absoluteFile.normalize() }
            .distinctBy { it.path }
            .sortedBy { it.path }
        require(entries.isNotEmpty()) { "depwalk classpath must not be empty" }
        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(entries.joinToString(separator = "\n", postfix = "\n") { it.path })
        }
    }
}
