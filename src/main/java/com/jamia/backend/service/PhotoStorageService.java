package com.jamia.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Saves, reads and deletes profile photo files in a folder on the server's disk.
 * This is the only class that knows photos are files on disk:
 * moving to AWS S3 later means changing only this class.
 */
@Service
public class PhotoStorageService {

    private final Path photosFolder;

    public PhotoStorageService(@Value("${jamia.storage.profile-photos-dir}") String photosFolder) {
        this.photosFolder = Path.of(photosFolder).toAbsolutePath().normalize();
        try {
            // Create the folder when the app starts, if it doesn't exist yet.
            Files.createDirectories(this.photosFolder);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create photos folder " + this.photosFolder, e);
        }
    }

    // Saves the image under a new random name (e.g. "3f2a...9c.jpg") and returns that name.
    // The user's own file name is never used, so it can't cause problems on disk.
    public String save(byte[] content, String extension) {
        String filename = UUID.randomUUID() + "." + extension;
        try {
            Files.write(resolve(filename), content);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save photo", e);
        }
        return filename;
    }

    public byte[] load(String filename) {
        try {
            return Files.readAllBytes(resolve(filename));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read photo " + filename, e);
        }
    }

    // Deletes the file. No error if it is already gone.
    public void delete(String filename) {
        try {
            Files.deleteIfExists(resolve(filename));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not delete photo " + filename, e);
        }
    }

    // Turns a file name into a full path and makes sure it stays inside the photos folder.
    // This blocks tricks like "../../application.properties" (called "path traversal").
    private Path resolve(String filename) {
        Path path = photosFolder.resolve(filename).normalize();
        if (!path.getParent().equals(photosFolder)) {
            throw new IllegalArgumentException("Invalid photo file name");
        }
        return path;
    }
}
