package com.minidoodle.controller;

import com.minidoodle.dto.BookMeetingRequest;
import com.minidoodle.dto.MeetingResponse;
import com.minidoodle.service.MeetingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

// All meeting operations are addressed through the owning user, mirroring the
// slot endpoints, so ownership scoping applies uniformly across the API.
@RestController
@RequestMapping("/api/v1/users/{userId}")
@RequiredArgsConstructor
public class MeetingController {

    private final MeetingService meetingService;

    @PostMapping("/slots/{slotId}/meeting")
    @ResponseStatus(HttpStatus.CREATED)
    public MeetingResponse bookMeeting(
            @PathVariable UUID userId,
            @PathVariable UUID slotId,
            @Valid @RequestBody BookMeetingRequest request
    ) {
        return meetingService.bookMeeting(userId, slotId, request);
    }

    @GetMapping("/meetings/{meetingId}")
    public MeetingResponse getMeeting(
            @PathVariable UUID userId,
            @PathVariable UUID meetingId
    ) {
        return meetingService.getMeeting(userId, meetingId);
    }

    @DeleteMapping("/meetings/{meetingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelMeeting(
            @PathVariable UUID userId,
            @PathVariable UUID meetingId
    ) {
        meetingService.cancelMeeting(userId, meetingId);
    }
}
