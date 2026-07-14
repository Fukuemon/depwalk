plugins {
    java
    application
    id("com.gradleup.shadow") version "9.5.1"
}

group = "com.fukuemon.depwalk"
version = "0.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.1")
    implementation("com.github.javaparser:javaparser-symbol-solver-core:3.28.2")
    implementation("org.soot-oss:sootup.core:2.0.0")
    implementation("org.soot-oss:sootup.java.core:2.0.0")
    implementation("org.soot-oss:sootup.java.bytecode.frontend:2.0.0")

    testImplementation(platform("org.junit:junit-bom:6.1.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.projectlombok:lombok:1.18.46")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("com.fukuemon.depwalk.javaanalyzer.Main")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveBaseName.set("java-analyzer")
    archiveClassifier.set("")
    archiveVersion.set("")
    manifest {
        attributes["Main-Class"] = "com.fukuemon.depwalk.javaanalyzer.Main"
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
