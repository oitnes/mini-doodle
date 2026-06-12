package com.minidoodle.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

// Element-level @NotNull rejects [null] entries (cascade @Valid alone skips
// null elements, which would NPE later); @Size mirrors the DB column width.
// The list cap bounds the per-request work (one user lookup per participant)
// so a single booking cannot be used as a resource-exhaustion vector.
public record BookMeetingRequest(
        @NotBlank @Size(max = 255) String title,
        String description,
        @NotNull @Size(max = 100) List<@Valid @NotNull ParticipantRequest> participants
) {}
