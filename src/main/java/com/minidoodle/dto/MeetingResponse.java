package com.minidoodle.dto;

import com.minidoodle.domain.Meeting;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MeetingResponse(
        UUID id,
        UUID slotId,
        String title,
        String description,
        Instant createdAt,
        List<ParticipantResponse> participants
) {
    public static MeetingResponse from(Meeting meeting) {
        List<ParticipantResponse> participants = meeting.getParticipants().stream()
                .map(ParticipantResponse::from)
                .toList();
        return new MeetingResponse(
                meeting.getId(),
                meeting.getSlot().getId(),
                meeting.getTitle(),
                meeting.getDescription(),
                meeting.getCreatedAt(),
                participants
        );
    }
}
