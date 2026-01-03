plugins {
    application
}

group = "com.fukuemon.depwalk"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.github.javaparser:javaparser-symbol-solver-core:3.26.2")
    implementation("com.google.code.gson:gson:2.11.0")
}

application {
    // TODO: set mainClass once implemented.
    mainClass = "com.fukuemon.depwalk.HelperMain"
}

tasks.register<Jar>("fatJar") {
    archiveClassifier.set("all")
    from(sourceSets.main.get().output)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith(".jar") }.map { zipTree(it) }
    })
    manifest {
        attributes["Main-Class"] = "com.fukuemon.depwalk.HelperMain"
    }
}



