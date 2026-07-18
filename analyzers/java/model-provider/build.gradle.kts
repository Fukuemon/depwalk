plugins {
    java
}

group = "com.fukuemon.depwalk"
version = "0.1.0"

// The provider runs inside the target build's Gradle daemon, whose JVM can be
// as old as Java 8 for Gradle 7.6.x (context/toolchain.md の matrix)。
// --release 8 keeps the classfile major at 52 and rejects newer JDK APIs at
// compile time.
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(8)
}

repositories {
    mavenCentral()
}

dependencies {
    // Compile baseline is the Gradle 7.6 line API (ADR-0006). The
    // redistributed API artifact line ends at 7.6.4; Gradle patch releases do
    // not change the public API, so compiling against 7.6.4 guarantees no
    // reference newer than the 7.6.5 runtime baseline.
    compileOnly("dev.gradleplugins:gradle-api:7.6.4")
}

tasks.jar {
    archiveBaseName.set("depwalk-gradle-model-provider")
    archiveVersion.set("")
}
