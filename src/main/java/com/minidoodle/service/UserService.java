package com.minidoodle.service;

import com.minidoodle.domain.Calendar;
import com.minidoodle.domain.SlotStatus;
import com.minidoodle.domain.TimeSlot;
import com.minidoodle.domain.User;
import com.minidoodle.dto.AvailabilityBlock;
import com.minidoodle.dto.CreateUserRequest;
import com.minidoodle.dto.UserResponse;
import com.minidoodle.exception.UserNotFoundException;
import com.minidoodle.repository.CalendarRepository;
import com.minidoodle.repository.TimeSlotRepository;
import com.minidoodle.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    // 366 days so an exactly-one-calendar-year window is legal across a leap
    // year. The cap protects the GiST index scan and the in-memory merge from
    // unbounded ranges.
    private static final Duration MAX_AVAILABILITY_WINDOW = Duration.ofDays(366);

    private final UserRepository userRepository;
    private final CalendarRepository calendarRepository;
    private final TimeSlotRepository timeSlotRepository;

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        User user = new User();
        user.setEmail(request.email());
        user.setDisplayName(request.displayName());
        userRepository.save(user);

        Calendar calendar = new Calendar();
        calendar.setUser(user);
        calendar.setTimezone(resolveTimezone(request.timezone()));
        calendarRepository.save(calendar);

        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public List<AvailabilityBlock> getAvailability(UUID userId, Instant from, Instant to, SlotStatus status) {
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("'from' must be before 'to'");
        }
        // Duration.between is overflow-safe for any parseable Instant pair
        // (epoch-millis arithmetic overflows for far-future instants).
        if (Duration.between(from, to).compareTo(MAX_AVAILABILITY_WINDOW) > 0) {
            throw new IllegalArgumentException("Availability window cannot exceed 1 year");
        }

        Calendar calendar = calendarRepository.findByUser_Id(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        List<TimeSlot> slots = status != null
                ? timeSlotRepository.findInRangeByStatus(calendar.getId(), from, to, status)
                : timeSlotRepository.findInRange(calendar.getId(), from, to);

        return mergeBlocks(slots);
    }

    /**
     * ARCHITECTURE DECISION: the "aggregated view" merges adjacent same-status
     * slots into contiguous blocks (four back-to-back FREE 30-min slots come
     * back as one 2h FREE block). Slots partially overlapping the window keep
     * their full range — block edges are deliberately not clipped to
     * [from, to), so the caller sees the true extent of each block.
     *
     * The merge runs in memory (one O(n) pass) rather than in SQL
     * (gaps-and-islands): with the window cap, one calendar yields at most a
     * few thousand rows. It lives here — not on the response DTO — because it
     * depends on the service-established preconditions that the slot list is
     * sorted by start and non-overlapping (the DB guarantees both).
     */
    private static List<AvailabilityBlock> mergeBlocks(List<TimeSlot> slots) {
        List<AvailabilityBlock> blocks = new ArrayList<>();
        Instant start = null;
        Instant end = null;
        SlotStatus blockStatus = null;
        List<UUID> ids = new ArrayList<>();

        for (TimeSlot slot : slots) {
            boolean continuesBlock = end != null
                    && slot.getStatus() == blockStatus
                    && slot.getStartAt().equals(end);
            if (continuesBlock) {
                end = slot.getEndAt();
                ids.add(slot.getId());
            } else {
                if (end != null) {
                    blocks.add(new AvailabilityBlock(start, end, blockStatus, List.copyOf(ids)));
                }
                start = slot.getStartAt();
                end = slot.getEndAt();
                blockStatus = slot.getStatus();
                ids = new ArrayList<>(List.of(slot.getId()));
            }
        }
        if (end != null) {
            blocks.add(new AvailabilityBlock(start, end, blockStatus, List.copyOf(ids)));
        }
        return blocks;
    }

    // Blank falls back to UTC; anything else must be a real IANA zone id —
    // garbage here would crash every client that calls ZoneId.of() on it.
    private static String resolveTimezone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return "UTC";
        }
        try {
            return ZoneId.of(timezone).getId();
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Invalid timezone: " + timezone);
        }
    }
}
