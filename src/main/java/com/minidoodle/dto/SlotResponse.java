package com.minidoodle.dto;

import com.minidoodle.domain.SlotStatus;
import com.minidoodle.domain.TimeSlot;

import java.time.Instant;
import java.util.UUID;

public record SlotResponse(UUID id, Instant startAt, Instant endAt, SlotStatus status, Instant createdAt) {

    public static SlotResponse from(TimeSlot slot) {
        return new SlotResponse(slot.getId(), slot.getStartAt(), slot.getEndAt(), slot.getStatus(), slot.getCreatedAt());
    }
}
