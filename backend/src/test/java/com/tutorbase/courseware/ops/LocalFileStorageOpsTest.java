package com.tutorbase.courseware.ops;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import com.tutorbase.courseware.storage.LocalFileStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class LocalFileStorageOpsTest {

    @TempDir
    Path root;

    @Test
    void listsBlobsWithSizesAndDeletesThem() {
        LocalFileStorage storage = new LocalFileStorage(root);
        byte[] first = "# 一".getBytes(StandardCharsets.UTF_8);
        byte[] second = "# 二".getBytes(StandardCharsets.UTF_8);
        String firstHash = storage.putBlob(7, first);
        String secondHash = storage.putBlob(7, second);

        var blobs = storage.listBlobs(7);
        assertThat(blobs).hasSize(2);
        assertThat(blobs.get(firstHash)).isEqualTo((long) first.length);
        assertThat(blobs.get(secondHash)).isEqualTo((long) second.length);
        assertThat(storage.listBlobs(8)).isEmpty();

        assertThat(storage.deleteBlob(7, firstHash)).isTrue();
        assertThat(storage.deleteBlob(7, firstHash)).isFalse();
        assertThat(storage.listBlobs(7)).hasSize(1);
        assertThat(storage.getBlob(7, secondHash)).isEqualTo(second);
    }
}
