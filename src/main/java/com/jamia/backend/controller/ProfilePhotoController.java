package com.jamia.backend.controller;

import com.jamia.backend.dto.ProfilePhoto;
import com.jamia.backend.service.ProfilePhotoService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.TimeUnit;

/**
 * HTTP endpoints for profile photos. All of them need a login token.
 * The rules live in ProfilePhotoService.
 */
@RestController
@RequestMapping("/api/users")
public class ProfilePhotoController {

    private final ProfilePhotoService profilePhotoService;

    public ProfilePhotoController(ProfilePhotoService profilePhotoService) {
        this.profilePhotoService = profilePhotoService;
    }

    // PUT /api/users/me/photo (multipart form field "photo") -> upload or replace your photo -> 204
    @PutMapping(value = "/me/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void uploadPhoto(@AuthenticationPrincipal Jwt jwt, @RequestParam("photo") MultipartFile photo) {
        try {
            profilePhotoService.uploadPhoto(currentUserId(jwt), photo.getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded photo", e);
        }
    }

    // DELETE /api/users/me/photo -> remove your photo -> 204
    @DeleteMapping("/me/photo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePhoto(@AuthenticationPrincipal Jwt jwt) {
        profilePhotoService.deletePhoto(currentUserId(jwt));
    }

    // GET /api/users/{userId}/photo -> the image (you, or someone who shares a room with you)
    @GetMapping("/{userId}/photo")
    public ResponseEntity<byte[]> getPhoto(@AuthenticationPrincipal Jwt jwt, @PathVariable Long userId) {
        ProfilePhoto photo = profilePhotoService.getPhoto(currentUserId(jwt), userId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.contentType()))
                // "private": only the user's phone may keep a copy (5 minutes), not shared internet caches.
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePrivate())
                .body(photo.content());
    }

    // The JWT "subject" is the user id we put in at login.
    private Long currentUserId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
