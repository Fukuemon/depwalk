plugins {
    java
}

group = "com.fukuemon.depwalk"
version = "0.1.0"

// provider は解析対象の build の Gradle daemon の中で動く。Gradle 7.6.x の daemon の
// JVM は Java 8 のことがある。--release 8 で classfile の major を 52 に保ち、Java 8 に
// ない JDK の API を compile の時点で弾く。
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(8)
}

repositories {
    mavenCentral()
}

dependencies {
    // 対応する最も古い Gradle の系列 7.6 の API に対して compile する。再配布された API の
    // artifact は 7.6.4 までしかない。Gradle の patch release は公開 API を変えないので、
    // 7.6.4 に対して compile すれば、実行時の下限 7.6.5 より新しい API を参照しない。
    compileOnly("dev.gradleplugins:gradle-api:7.6.4")
}

tasks.jar {
    archiveBaseName.set("depwalk-gradle-model-provider")
    archiveVersion.set("")
}
