package com.jamia.backend.service;

import com.jamia.backend.dto.ProfilePhoto;
import com.jamia.backend.entity.User;
import com.jamia.backend.exception.BadRequestException;
import com.jamia.backend.exception.ResourceNotFoundException;
import com.jamia.backend.exception.UserNotFoundException;
import com.jamia.backend.repository.RoomMemberRepository;
import com.jamia.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Business rules for profile photos: upload/replace, delete, and who may see a photo.
 * The files themselves are handled by PhotoStorageService.
 */
@Service
public class ProfilePhotoService {

    public static final int MAX_PHOTO_BYTES = 5 * 1024 * 1024; // 5 MB

    // The first bytes ("signature") of every JPEG and PNG file.
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private final UserRepository userRepository;
    private final RoomMemberRepository memberRepository;
    private final PhotoStorageService photoStorage;

    public ProfilePhotoService(UserRepository userRepository,
                               RoomMemberRepository memberRepository,
                               PhotoStorageService photoStorage) {
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
        this.photoStorage = photoStorage;
    }

    // Saves a new photo for the user. If they already had one, the old file is removed.
    @Transactional
    public void uploadPhoto(Long userId, byte[] content) {
        if (content == null || content.length == 0) {
            throw new BadRequestException("The photo is empty");
        }
        if (content.length > MAX_PHOTO_BYTES) {
            throw new BadRequestException("The photo must be at most 5 MB");
        }
        String extension = detectExtension(content);

        User user = findUser(userId);
        String oldFilename = user.getProfilePhotoFilename();

        user.setProfilePhotoFilename(photoStorage.save(content, extension));

        if (oldFilename != null) {
            deleteFileAfterCommit(oldFilename);
        }
    }

    // Removes the user's photo (if any).
    @Transactional
    public void deletePhoto(Long userId) {
        User user = findUser(userId);
        String filename = user.getProfilePhotoFilename();
        if (filename != null) {
            user.setProfilePhotoFilename(null);
            deleteFileAfterCommit(filename);
        }
    }

    // Returns a user's photo. Allowed for yourself, or for someone who shares a room with you.
    // Everyone else gets "not found", so they can't even tell whether a photo exists.
    @Transactional(readOnly = true)
    public ProfilePhoto getPhoto(Long requesterId, Long userId) {
        boolean allowed = requesterId.equals(userId) || memberRepository.shareARoom(requesterId, userId);
        if (!allowed) {
            throw new ResourceNotFoundException("Photo not found");
        }

        String filename = userRepository.findById(userId)
                .map(User::getProfilePhotoFilename)
                .orElseThrow(() -> new ResourceNotFoundException("Photo not found"));
        if (filename == null) {
            throw new ResourceNotFoundException("Photo not found");
        }

        String contentType = filename.endsWith(".png") ? "image/png" : "image/jpeg";
        return new ProfilePhoto(photoStorage.load(filename), contentType);
    }

    // Looks at the file's real first bytes. The file name and "content type" sent by the app
    // can be faked; the signature can't (without breaking the image).
    private String detectExtension(byte[] content) {
        if (startsWith(content, JPEG_SIGNATURE)) {
            return "jpg";
        }
        if (startsWith(content, PNG_SIGNATURE)) {
            return "png";
        }
        throw new BadRequestException("Only JPEG and PNG photos are allowed");
    }

    private static boolean startsWith(byte[] content, byte[] signature) {
        if (content.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (content[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }

    // Deletes the file only after the database change is saved (committed).
    // If the transaction fails, the database still points to the old file, so it must stay.
    private void deleteFileAfterCommit(String filename) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                photoStorage.delete(filename);
            }
        });
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }
}
