package com.minidoodle.dto;

import com.minidoodle.domain.SlotStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One contiguous run of same-status time in the availability view.
 * {@code slotIds} keeps the link back to the underlying merged slots so
 * callers can still act on them (e.g. pick one to book). The merge algorithm
 * lives in UserService, next to the preconditions it depends on.
 */
public record AvailabilityBlock(Instant startAt, Instant endAt, SlotStatus status, List<UUID> slotIds) {
}
