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
    // gradle-tooling-api は Gradle 公式の libs-releases repository で配布される。
    maven {
        url = uri("https://repo.gradle.org/gradle/libs-releases")
    }
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.1")
    implementation("com.github.javaparser:javaparser-symbol-solver-core:3.28.2")
    implementation("org.soot-oss:sootup.core:2.0.0")
    implementation("org.soot-oss:sootup.java.core:2.0.0")
    implementation("org.soot-oss:sootup.java.bytecode.frontend:2.0.0")
    implementation("org.gradle:gradle-tooling-api:9.6.1")
    // Tooling API は slf4j 経由で log を出す。Analyzer の stdout は Protocol
    // 専用・stderr は固定文のみのため、binding を nop に固定して Gradle 由来
    // の log 出力を遮断する (ADR-0006 の output 隔離)。
    runtimeOnly("org.slf4j:slf4j-nop:2.0.17")

    testImplementation(platform("org.junit:junit-bom:6.1.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.projectlombok:lombok:1.18.46")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// custom model provider jar を resource として同梱し、実行時に一時 directory
// へ展開して init script から Gradle daemon に注入する。
tasks.processResources {
    from(project(":model-provider").tasks.named("jar")) {
        into("gradle-model-provider")
    }
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
