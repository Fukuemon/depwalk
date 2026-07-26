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
    // 外部ライブラリ隔離 (ADR-0007) の機械検査。archunit-junit5 の TestEngine ではなく
    // core を使い、既存の Jupiter @Test から ArchRule.check() を呼ぶ (JUnit Platform 6 と
    // ArchUnit の JUnit5 engine のバージョン整合を持ち込まないため)。
    testImplementation("com.tngtech.archunit:archunit:1.4.1")
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
    useJUnitPlatform {
        excludeTags("gradle-compat")
    }
    // MultiModuleFixtureEquivalenceTest 等の実 jar テストが最新実装の
    // shadowJar を参照する (事前生成済み・stale な jar を検証しない)。
    dependsOn(tasks.shadowJar)
}

// Gradle discovery compatibility matrix (context/toolchain.md の CI anchor)。
// daemon JDK は toolchain 解決 (foojay) で供給し、未解決 anchor は fail させる (skip 成功にしない)。
val matrixJdkMajors = listOf(8, 17, 25)
tasks.register<Test>("gradleCompatibilityTest") {
    description = "Gradle 7.6.5/8.14.5/9.6.1 × daemon JDK 8/17/25 の discovery 互換性 matrix"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags("gradle-compat")
    }
    matrixJdkMajors.forEach { major ->
        val launcher = javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(major))
        }
        jvmArgumentProviders.add(CommandLineArgumentProvider {
            listOf("-Ddepwalk.matrix.jdk$major=" + launcher.get().metadata.installationPath.asFile.absolutePath)
        })
    }
    shouldRunAfter(tasks.test)
}

tasks.shadowJar {
    archiveBaseName.set("java-analyzer")
    archiveClassifier.set("")
    archiveVersion.set("")
    manifest {
        attributes["Main-Class"] = "com.fukuemon.depwalk.javaanalyzer.Main"
        // Tooling API の native-platform load による JVM WARNING を抑止し、
        // Analyzer stderr を depwalk 生成の固定行だけに保つ (output 隔離)。
        attributes["Enable-Native-Access"] = "ALL-UNNAMED"
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
