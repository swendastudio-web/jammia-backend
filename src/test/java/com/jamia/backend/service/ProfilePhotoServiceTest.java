package com.jamia.backend.service;

import com.jamia.backend.dto.ProfilePhoto;
import com.jamia.backend.entity.User;
import com.jamia.backend.exception.BadRequestException;
import com.jamia.backend.exception.ResourceNotFoundException;
import com.jamia.backend.repository.RoomMemberRepository;
import com.jamia.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the profile photo rules. Repositories and file storage are mocks.
 */
@ExtendWith(MockitoExtension.class)
class ProfilePhotoServiceTest {

    // Smallest byte arrays that start like real JPEG / PNG files.
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 1, 2, 3};

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoomMemberRepository memberRepository;
    @Mock
    private PhotoStorageService photoStorage;

    private ProfilePhotoService photoService;
    private User user;

    @BeforeEach
    void setUp() {
        photoService = new ProfilePhotoService(userRepository, memberRepository, photoStorage);
        user = new User("Ali", "Hassan", "ali@mail.com", "hash");
        ReflectionTestUtils.setField(user, "id", 1L);
        // Pretend a transaction is running, so "delete after commit" can be registered.
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void upload_rejectsAnEmptyFile() {
        assertThatThrownBy(() -> photoService.uploadPhoto(1L, new byte[0]))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("The photo is empty");
    }

    @Test
    void upload_rejectsAFileBiggerThan5Mb() {
        byte[] tooBig = Arrays.copyOf(JPEG, ProfilePhotoService.MAX_PHOTO_BYTES + 1);

        assertThatThrownBy(() -> photoService.uploadPhoto(1L, tooBig))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("The photo must be at most 5 MB");
    }

    @Test
    void upload_rejectsAFileThatIsNotReallyJpegOrPng() {
        byte[] text = "this is not an image".getBytes();

        assertThatThrownBy(() -> photoService.uploadPhoto(1L, text))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Only JPEG and PNG photos are allowed");
        verify(photoStorage, never()).save(any(), anyString());
    }

    @Test
    void upload_recognisesJpegAndPngByTheirBytes() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(photoStorage.save(JPEG, "jpg")).thenReturn("a.jpg");
        when(photoStorage.save(PNG, "png")).thenReturn("b.png");

        photoService.uploadPhoto(1L, JPEG);
        assertThat(user.getProfilePhotoFilename()).isEqualTo("a.jpg");

        photoService.uploadPhoto(1L, PNG);
        assertThat(user.getProfilePhotoFilename()).isEqualTo("b.png");
    }

    @Test
    void upload_deletesTheOldFileOnlyAfterTheDatabaseChangeIsCommitted() {
        user.setProfilePhotoFilename("old.jpg");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(photoStorage.save(JPEG, "jpg")).thenReturn("new.jpg");

        photoService.uploadPhoto(1L, JPEG);

        assertThat(user.getProfilePhotoFilename()).isEqualTo("new.jpg");
        verify(photoStorage, never()).delete(anyString());   // not yet: nothing committed

        simulateCommit();

        verify(photoStorage).delete("old.jpg");
    }

    @Test
    void delete_clearsTheNameAndDeletesTheFileAfterCommit() {
        user.setProfilePhotoFilename("old.jpg");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        photoService.deletePhoto(1L);

        assertThat(user.getProfilePhotoFilename()).isNull();
        verify(photoStorage, never()).delete(anyString());
        simulateCommit();
        verify(photoStorage).delete("old.jpg");
    }

    @Test
    void getPhoto_ownerCanSeeTheirPhoto() {
        user.setProfilePhotoFilename("me.png");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(photoStorage.load("me.png")).thenReturn(PNG);

        ProfilePhoto photo = photoService.getPhoto(1L, 1L);

        assertThat(photo.contentType()).isEqualTo("image/png");
        assertThat(photo.content()).isEqualTo(PNG);
    }

    @Test
    void getPhoto_someoneWhoSharesARoomCanSeeIt() {
        user.setProfilePhotoFilename("me.jpg");
        when(memberRepository.shareARoom(2L, 1L)).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(photoStorage.load("me.jpg")).thenReturn(JPEG);

        assertThat(photoService.getPhoto(2L, 1L).contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void getPhoto_strangersGetNotFound() {
        when(memberRepository.shareARoom(3L, 1L)).thenReturn(false);

        assertThatThrownBy(() -> photoService.getPhoto(3L, 1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Photo not found");
        verify(photoStorage, never()).load(anyString());
    }

    @Test
    void getPhoto_userWithoutAPhotoGivesNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> photoService.getPhoto(1L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(memberRepository, never()).shareARoom(eq(1L), any());
    }

    // Runs what Spring would run right after a successful commit.
    private static void simulateCommit() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }
}
