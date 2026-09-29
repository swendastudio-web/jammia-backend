package com.jamia.backend.dto;

/**
 * A profile photo ready to send to the app: the image bytes and their type.
 *
 * @param content     the image file
 * @param contentType "image/jpeg" or "image/png"
 */
public record ProfilePhoto(
        byte[] content,
        String contentType
) {
}
