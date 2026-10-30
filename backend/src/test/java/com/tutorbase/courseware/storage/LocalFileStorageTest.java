package com.tutorbase.courseware.storage;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileStorageTest {

    @TempDir
    Path root;

    @Test
    void storesBlobsByContentHashAndDeduplicates() throws Exception {
        LocalFileStorage storage = new LocalFileStorage(root);
        byte[] content = "# 原电池".getBytes(StandardCharsets.UTF_8);

        String first = storage.putBlob(7, content);
        String second = storage.putBlob(7, content);

        assertThat(first).matches("sha256:[0-9a-f]{64}");
        assertThat(second).isEqualTo(first);
        assertThat(storage.getBlob(7, first)).isEqualTo(content);
        try (var blobs = Files.list(root.resolve("7").resolve("blobs"))) {
            assertThat(blobs.count()).isEqualTo(1);
        }
    }

    @Test
    void isolatesTeacherNamespaces() {
        LocalFileStorage storage = new LocalFileStorage(root);
        String hash = storage.putBlob(7, "老师七".getBytes(StandardCharsets.UTF_8));

        assertThat(storage.getBlob(8, hash)).isNull();
    }

    @Test
    void returnsNullForMissingBlob() {
        LocalFileStorage storage = new LocalFileStorage(root);
        assertThat(storage.getBlob(7, "sha256:" + "0".repeat(64))).isNull();
    }

    @Test
    void rejectsMalformedHash() {
        LocalFileStorage storage = new LocalFileStorage(root);
        assertThatThrownBy(() -> storage.getBlob(7, "../../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void writesVersionedManifest() throws Exception {
        LocalFileStorage storage = new LocalFileStorage(root);
        storage.putManifest(7, "高中化学", 3, "{\"version\":3}".getBytes(StandardCharsets.UTF_8));

        Path manifest = root.resolve("7").resolve("manifests").resolve("高中化学").resolve("v3.json");
        assertThat(Files.exists(manifest)).isTrue();
        assertThat(Files.readString(manifest)).contains("\"version\":3");
    }
}
