package com.minidoodle.controller;

import com.minidoodle.dto.BookMeetingRequest;
import com.minidoodle.dto.MeetingResponse;
import com.minidoodle.service.MeetingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class MeetingController {

    private final MeetingService meetingService;

    @PostMapping("/api/v1/users/{userId}/slots/{slotId}/meeting")
    @ResponseStatus(HttpStatus.CREATED)
    public MeetingResponse bookMeeting(
            @PathVariable UUID userId,
            @PathVariable UUID slotId,
            @Valid @RequestBody BookMeetingRequest request
    ) {
        return meetingService.bookMeeting(userId, slotId, request);
    }

    @GetMapping("/api/v1/meetings/{meetingId}")
    public MeetingResponse getMeeting(@PathVariable UUID meetingId) {
        return meetingService.getMeeting(meetingId);
    }

    @DeleteMapping("/api/v1/meetings/{meetingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelMeeting(@PathVariable UUID meetingId) {
        meetingService.cancelMeeting(meetingId);
    }
}
