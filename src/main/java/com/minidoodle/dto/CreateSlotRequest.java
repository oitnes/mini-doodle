package com.minidoodle.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;

public record CreateSlotRequest(
        @NotNull Instant startAt,
        Instant endAt,
        @Positive Integer durationMinutes
) {}
