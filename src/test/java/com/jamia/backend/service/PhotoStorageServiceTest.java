package com.jamia.backend.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests saving, reading and deleting photo files.
 * @TempDir gives each test a fresh temporary folder that JUnit deletes afterwards,
 * so the real uploads folder is never touched.
 */
class PhotoStorageServiceTest {

    @TempDir
    Path tempFolder;

    @Test
    void createsThePhotosFolderIfItDoesNotExist() {
        Path photos = tempFolder.resolve("profile-photos");

        new PhotoStorageService(photos.toString());

        assertThat(photos).isDirectory();
    }

    @Test
    void savesUnderARandomNameAndLoadsTheSameBytes() {
        PhotoStorageService storage = new PhotoStorageService(tempFolder.toString());

        String name = storage.save(new byte[]{1, 2, 3}, "jpg");

        assertThat(name).matches("[0-9a-f-]{36}\\.jpg");
        assertThat(storage.load(name)).containsExactly(1, 2, 3);
    }

    @Test
    void twoSavesNeverGetTheSameName() {
        PhotoStorageService storage = new PhotoStorageService(tempFolder.toString());

        assertThat(storage.save(new byte[]{1}, "png")).isNotEqualTo(storage.save(new byte[]{1}, "png"));
    }

    @Test
    void deletesTheFileAndDeletingTwiceIsFine() {
        PhotoStorageService storage = new PhotoStorageService(tempFolder.toString());
        String name = storage.save(new byte[]{1, 2, 3}, "jpg");

        storage.delete(name);
        storage.delete(name);

        assertThat(Files.exists(tempFolder.resolve(name))).isFalse();
    }

    @Test
    void blocksNamesThatLeaveThePhotosFolder() throws Exception {
        Path photos = tempFolder.resolve("photos");
        PhotoStorageService storage = new PhotoStorageService(photos.toString());
        Files.writeString(tempFolder.resolve("secret.txt"), "private");

        for (String badName : new String[]{"../secret.txt", "../../etc/passwd", "sub/photo.jpg", ""}) {
            assertThatThrownBy(() -> storage.load(badName))
                    .as(badName)
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> storage.delete(badName))
                    .as(badName)
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(tempFolder.resolve("secret.txt")).exists();
    }
}
