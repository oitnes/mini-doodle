package com.minidoodle.dto;

import com.minidoodle.domain.Participant;
import com.minidoodle.domain.ResponseStatus;

import java.util.UUID;

public record ParticipantResponse(UUID userId, String email, ResponseStatus responseStatus) {

    public static ParticipantResponse from(Participant p) {
        UUID userId = p.getUser() != null ? p.getUser().getId() : null;
        return new ParticipantResponse(userId, p.getEmail(), p.getResponseStatus());
    }
}
