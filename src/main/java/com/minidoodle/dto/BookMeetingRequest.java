package com.minidoodle.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record BookMeetingRequest(
        @NotBlank String title,
        String description,
        @NotNull List<@Valid ParticipantRequest> participants
) {}
