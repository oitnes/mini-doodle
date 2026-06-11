package com.minidoodle.dto;

import com.minidoodle.domain.SlotStatus;

import java.time.Instant;

public record UpdateSlotRequest(
        Instant startAt,
        Instant endAt,
        SlotStatus status
) {}
