package com.jamia.backend.service;

import com.jamia.backend.dto.InviteLinkResponse;
import com.jamia.backend.dto.InvitePreviewResponse;
import com.jamia.backend.dto.JoinRequestResponse;
import com.jamia.backend.entity.JoinRequest;
import com.jamia.backend.entity.JoinRequestStatus;
import com.jamia.backend.entity.RoomInviteLink;
import com.jamia.backend.entity.RoomMember;
import com.jamia.backend.entity.RoomStatus;
import com.jamia.backend.entity.SavingsRoom;
import com.jamia.backend.entity.User;
import com.jamia.backend.exception.BusinessRuleException;
import com.jamia.backend.exception.ForbiddenActionException;
import com.jamia.backend.exception.ResourceNotFoundException;
import com.jamia.backend.exception.UserNotFoundException;
import com.jamia.backend.repository.JoinRequestRepository;
import com.jamia.backend.repository.RoomInviteLinkRepository;
import com.jamia.backend.repository.RoomMemberRepository;
import com.jamia.backend.repository.SavingsRoomRepository;
import com.jamia.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

/**
 * Business rules for invitations:
 * any member shares their own link; people who open it ask to join;
 * only the room creator approves, seeing who asks and which member referred them.
 */
@Service
public class InvitationService {

    private final RoomInviteLinkRepository inviteLinkRepository;
    private final JoinRequestRepository joinRequestRepository;
    private final RoomMemberRepository memberRepository;
    private final SavingsRoomRepository roomRepository;
    private final UserRepository userRepository;
    private final AppSettingsService appSettingsService;
    private final String inviteBaseUrl;
    private final SecureRandom secureRandom = new SecureRandom();

    public InvitationService(RoomInviteLinkRepository inviteLinkRepository,
                             JoinRequestRepository joinRequestRepository,
                             RoomMemberRepository memberRepository,
                             SavingsRoomRepository roomRepository,
                             UserRepository userRepository,
                             AppSettingsService appSettingsService,
                             @Value("${jamia.invite.base-url}") String inviteBaseUrl) {
        this.inviteLinkRepository = inviteLinkRepository;
        this.joinRequestRepository = joinRequestRepository;
        this.memberRepository = memberRepository;
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
        this.appSettingsService = appSettingsService;
        this.inviteBaseUrl = inviteBaseUrl;
    }

    // A member's personal link for the room. Reuses their still-valid link, or creates a new one
    // valid for the number of days the admin has set.
    @Transactional
    public InviteLinkResponse getOrCreateMyInviteLink(Long roomId, Long userId) {
        RoomMember member = memberRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found"));
        SavingsRoom room = member.getRoom();
        if (room.getStatus() != RoomStatus.OPEN) {
            throw new BusinessRuleException("This room has already started; nobody can join now");
        }

        RoomInviteLink link = inviteLinkRepository
                .findFirstByRoomIdAndCreatedByIdAndExpiresAtAfterOrderByExpiresAtDesc(
                        roomId, member.getId(), LocalDateTime.now())
                .orElseGet(() -> {
                    int validDays = appSettingsService.getSettings().getInviteLinkValidDays();
                    return inviteLinkRepository.save(new RoomInviteLink(room, member, newToken(),
                            LocalDateTime.now().plusDays(validDays)));
                });

        return new InviteLinkResponse(link.getToken(), inviteBaseUrl + link.getToken(), link.getExpiresAt());
    }

    // What the person sees after opening the link.
    @Transactional(readOnly = true)
    public InvitePreviewResponse previewInvite(String token, Long userId) {
        RoomInviteLink link = findValidLink(token);
        SavingsRoom room = link.getRoom();

        return new InvitePreviewResponse(
                room.getName(),
                room.getDescription(),
                room.getContributionAmount(),
                room.getCurrency(),
                room.getFrequency(),
                memberRepository.countByRoomId(room.getId()),
                room.getMaxMembers(),
                fullName(room.getCreator()),
                fullName(link.getCreatedBy().getUser()),
                memberRepository.existsByRoomIdAndUserId(room.getId(), userId),
                joinRequestRepository.existsByRoomIdAndUserIdAndStatus(room.getId(), userId, JoinRequestStatus.PENDING)
        );
    }

    // The person asks to join. The member whose link was used is saved as the referrer.
    @Transactional
    public JoinRequestResponse requestToJoin(String token, Long userId) {
        RoomInviteLink link = findValidLink(token);
        SavingsRoom room = link.getRoom();

        if (room.getStatus() != RoomStatus.OPEN) {
            throw new BusinessRuleException("This room has already started; nobody can join now");
        }
        if (memberRepository.existsByRoomIdAndUserId(room.getId(), userId)) {
            throw new BusinessRuleException("You are already a member of this room");
        }
        if (joinRequestRepository.existsByRoomIdAndUserIdAndStatus(room.getId(), userId, JoinRequestStatus.PENDING)) {
            throw new BusinessRuleException("You have already asked to join this room");
        }
        if (memberRepository.countByRoomId(room.getId()) >= room.getMaxMembers()) {
            throw new BusinessRuleException("This room is full");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        try {
            // saveAndFlush: the database's "one pending request per person" rule is checked right here.
            JoinRequest request = joinRequestRepository.saveAndFlush(
                    new JoinRequest(room, user, link.getCreatedBy()));
            return JoinRequestResponse.from(request);
        } catch (DataIntegrityViolationException ex) {
            // The same person tapped twice at the same moment.
            throw new BusinessRuleException("You have already asked to join this room");
        }
    }

    // The room creator's list of waiting requests.
    @Transactional(readOnly = true)
    public List<JoinRequestResponse> getPendingRequests(Long roomId, Long userId) {
        requireCreator(roomId, userId);
        return joinRequestRepository.findByRoomIdAndStatusOrderByCreatedAtAsc(roomId, JoinRequestStatus.PENDING)
                .stream().map(JoinRequestResponse::from).toList();
    }

    // Creator accepts: the person becomes a member (if the room is still open and has space).
    @Transactional
    public JoinRequestResponse approve(Long roomId, Long requestId, Long userId) {
        // Lock the room so two approvals can't take the last seat at the same moment.
        SavingsRoom room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found"));
        requireCreator(roomId, userId);
        JoinRequest request = findPendingRequest(roomId, requestId);

        if (room.getStatus() != RoomStatus.OPEN) {
            throw new BusinessRuleException("This room has already started; nobody can join now");
        }
        if (memberRepository.countByRoomId(roomId) >= room.getMaxMembers()) {
            throw new BusinessRuleException("This room is full");
        }
        if (!memberRepository.existsByRoomIdAndUserId(roomId, request.getUser().getId())) {
            memberRepository.save(new RoomMember(room, request.getUser()));
        }

        request.approve();
        return JoinRequestResponse.from(request);
    }

    // Creator declines. The person may ask again later with a valid link.
    @Transactional
    public JoinRequestResponse reject(Long roomId, Long requestId, Long userId) {
        requireCreator(roomId, userId);
        JoinRequest request = findPendingRequest(roomId, requestId);
        request.reject();
        return JoinRequestResponse.from(request);
    }

    // ----- helpers -----

    // Unknown and expired links get the same message, so nobody can probe which tokens exist.
    private RoomInviteLink findValidLink(String token) {
        return inviteLinkRepository.findByToken(token)
                .filter(link -> !link.isExpired())
                .orElseThrow(() -> new ResourceNotFoundException("This invite link is not valid or has expired"));
    }

    // Non-members get 404 (can't tell the room exists); members who aren't the creator get 403.
    private void requireCreator(Long roomId, Long userId) {
        RoomMember member = memberRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found"));
        if (!member.getRoom().isCreatedBy(userId)) {
            throw new ForbiddenActionException("Only the room creator can manage join requests");
        }
    }

    private JoinRequest findPendingRequest(Long roomId, Long requestId) {
        JoinRequest request = joinRequestRepository.findByIdAndRoomId(requestId, roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Join request not found"));
        if (request.getStatus() != JoinRequestStatus.PENDING) {
            throw new BusinessRuleException("This request is already " + request.getStatus());
        }
        return request;
    }

    // 24 random bytes -> 32 URL-safe characters. Impossible to guess.
    private String newToken() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String fullName(User user) {
        return user.getFirstName() + " " + user.getLastName();
    }
}
