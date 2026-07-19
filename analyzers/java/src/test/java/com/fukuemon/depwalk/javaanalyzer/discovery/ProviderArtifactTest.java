package com.fukuemon.depwalk.javaanalyzer.discovery;

import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 同梱 provider artifact の binary 境界を検証する: Gradle 7.6.x daemon の
 * Java 8 で load できる classfile major 52 のみで構成され、Analyzer 本体の
 * JDK 25 class を含まない。
 */
class ProviderArtifactTest {

    @Test
    void bundledProviderContainsOnlyJava8ProviderClasses() throws Exception {
        List<String> classEntries = new ArrayList<>();
        InputStream resource =
                getClass().getResourceAsStream("/gradle-model-provider/depwalk-gradle-model-provider.jar");
        org.junit.jupiter.api.Assertions.assertNotNull(resource,
                "provider jar is not bundled; run the model-provider jar packaging first");
        try (resource; ZipInputStream zip = new ZipInputStream(resource)) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                    continue;
                }
                String name = entry.getName();
                classEntries.add(name);
                DataInputStream data = new DataInputStream(zip);
                int magic = data.readInt();
                assertTrue(magic == 0xCAFEBABE, () -> name + " is not a classfile");
                data.readUnsignedShort();
                int major = data.readUnsignedShort();
                assertTrue(major == 52,
                        () -> name + " classfile major = " + major + ", want 52 (Java 8)");
                assertTrue(name.startsWith("com/fukuemon/depwalk/gradleprovider/"),
                        () -> name + " is outside the provider package");
                assertFalse(name.startsWith("com/fukuemon/depwalk/javaanalyzer/"),
                        () -> name + " leaks an analyzer class into the provider artifact");
            }
        }
        assertFalse(classEntries.isEmpty(), "provider artifact must contain provider classes");
    }
}
