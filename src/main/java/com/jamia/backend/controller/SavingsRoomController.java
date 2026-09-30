package com.jamia.backend.controller;

import com.jamia.backend.dto.CreateRoomRequest;
import com.jamia.backend.dto.RoomResponse;
import com.jamia.backend.dto.RoomSummaryResponse;
import com.jamia.backend.dto.RoundResponse;
import com.jamia.backend.dto.UpdateMaxMembersRequest;
import com.jamia.backend.dto.StartRoomRequest;
import com.jamia.backend.service.SavingsRoomService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * HTTP endpoints for savings rooms. All of them need a login token.
 * The rules live in SavingsRoomService.
 */
@RestController
@RequestMapping("/api/rooms")
public class SavingsRoomController {

    private final SavingsRoomService roomService;

    public SavingsRoomController(SavingsRoomService roomService) {
        this.roomService = roomService;
    }

    // POST /api/rooms -> create a room (you become its first member) -> 201
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomResponse createRoom(@AuthenticationPrincipal Jwt jwt,
                                   @Valid @RequestBody CreateRoomRequest request) {
        return roomService.createRoom(currentUserId(jwt), request.name(), request.description(),
                request.contributionAmount(), request.currency(), request.frequency(), request.maxMembers());
    }

    // GET /api/rooms -> the rooms you belong to
    @GetMapping
    public List<RoomSummaryResponse> getMyRooms(@AuthenticationPrincipal Jwt jwt) {
        return roomService.getMyRooms(currentUserId(jwt));
    }

    // GET /api/rooms/{roomId} -> one room with its members (members only; others get 404)
    @GetMapping("/{roomId}")
    public RoomResponse getRoom(@AuthenticationPrincipal Jwt jwt, @PathVariable Long roomId) {
        return roomService.getRoom(roomId, currentUserId(jwt));
    }

    // POST /api/rooms/{roomId}/start -> admin starts a round: turn order fixed, payments created
    @PostMapping("/{roomId}/start")
    public RoomResponse startRoom(@AuthenticationPrincipal Jwt jwt, @PathVariable Long roomId,
                                  @Valid @RequestBody StartRoomRequest request) {
        return roomService.startRoom(roomId, currentUserId(jwt), request.turnOrderMethod(),
                request.startDate(), request.memberOrder());
    }

    // PUT /api/rooms/{roomId}/max-members -> admin changes the room size (between rounds)
    @PutMapping("/{roomId}/max-members")
    public RoomResponse updateMaxMembers(@AuthenticationPrincipal Jwt jwt, @PathVariable Long roomId,
                                         @Valid @RequestBody UpdateMaxMembersRequest request) {
        return roomService.updateMaxMembers(roomId, currentUserId(jwt), request.maxMembers());
    }

    // DELETE /api/rooms/{roomId}/members/{memberUserId} -> admin removes a member (between rounds)
    @DeleteMapping("/{roomId}/members/{memberUserId}")
    public RoomResponse removeMember(@AuthenticationPrincipal Jwt jwt, @PathVariable Long roomId,
                                     @PathVariable Long memberUserId) {
        return roomService.removeMember(roomId, currentUserId(jwt), memberUserId);
    }

    // POST /api/rooms/{roomId}/leave -> a member leaves the room (between rounds) -> 204
    @PostMapping("/{roomId}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leaveRoom(@AuthenticationPrincipal Jwt jwt, @PathVariable Long roomId) {
        roomService.leaveRoom(roomId, currentUserId(jwt));
    }

    // GET /api/rooms/{roomId}/rounds -> rounds history, newest first
    @GetMapping("/{roomId}/rounds")
    public List<RoundResponse> getRounds(@AuthenticationPrincipal Jwt jwt, @PathVariable Long roomId) {
        return roomService.getRounds(roomId, currentUserId(jwt));
    }

    // The JWT "subject" is the user id we put in at login.
    private Long currentUserId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
