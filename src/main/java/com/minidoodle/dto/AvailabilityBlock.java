package com.minidoodle.dto;

import com.minidoodle.domain.SlotStatus;
import com.minidoodle.domain.TimeSlot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One contiguous run of same-status time. The availability endpoint merges
 * adjacent slots with equal status into a single block; {@code slotIds} keeps
 * the link back to the underlying slots so callers can still act on them
 * (e.g. pick one to book).
 */
public record AvailabilityBlock(Instant startAt, Instant endAt, SlotStatus status, List<UUID> slotIds) {

    /**
     * Merges a list of slots (sorted by startAt, non-overlapping — guaranteed
     * by the DB exclusion constraint) into contiguous same-status blocks.
     *
     * ARCHITECTURE DECISION: aggregation happens in memory, per request,
     * rather than in SQL (window functions / gaps-and-islands). The window is
     * capped at one year and a single calendar's slots in a window are at most
     * a few thousand rows, so an O(n) pass is simpler and fast enough. Revisit
     * if availability ever spans many calendars in one query.
     */
    public static List<AvailabilityBlock> merge(List<TimeSlot> slots) {
        List<AvailabilityBlock> blocks = new ArrayList<>();
        Instant start = null;
        Instant end = null;
        SlotStatus status = null;
        List<UUID> ids = new ArrayList<>();

        for (TimeSlot slot : slots) {
            boolean continuesBlock = end != null
                    && slot.getStatus() == status
                    && slot.getStartAt().equals(end);
            if (continuesBlock) {
                end = slot.getEndAt();
                ids.add(slot.getId());
            } else {
                if (end != null) {
                    blocks.add(new AvailabilityBlock(start, end, status, List.copyOf(ids)));
                }
                start = slot.getStartAt();
                end = slot.getEndAt();
                status = slot.getStatus();
                ids = new ArrayList<>(List.of(slot.getId()));
            }
        }
        if (end != null) {
            blocks.add(new AvailabilityBlock(start, end, status, List.copyOf(ids)));
        }
        return blocks;
    }
}
