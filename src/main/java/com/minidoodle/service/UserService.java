package com.minidoodle.service;

import com.minidoodle.domain.Calendar;
import com.minidoodle.domain.User;
import com.minidoodle.dto.AvailabilityBlock;
import com.minidoodle.dto.CreateUserRequest;
import com.minidoodle.dto.UserResponse;
import com.minidoodle.exception.UserNotFoundException;
import com.minidoodle.repository.CalendarRepository;
import com.minidoodle.repository.TimeSlotRepository;
import com.minidoodle.repository.UserRepository;
import com.minidoodle.domain.SlotStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

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
        calendar.setTimezone(request.timezone() != null ? request.timezone() : "UTC");
        calendarRepository.save(calendar);

        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public List<AvailabilityBlock> getAvailability(UUID userId, Instant from, Instant to, SlotStatus status) {
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("'from' must be before 'to'");
        }
        // Hard cap keeps a single request from scanning an unbounded range —
        // protects the GiST index scan and the in-memory merge below.
        if (to.toEpochMilli() - from.toEpochMilli() > 365L * 24 * 60 * 60 * 1000) {
            throw new IllegalArgumentException("Availability window cannot exceed 1 year");
        }

        com.minidoodle.domain.Calendar calendar = calendarRepository.findByUser_Id(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        List<com.minidoodle.domain.TimeSlot> slots = status != null
                ? timeSlotRepository.findInRangeByStatus(calendar.getId(), from, to, status)
                : timeSlotRepository.findInRange(calendar.getId(), from, to);

        // ARCHITECTURE DECISION: the "aggregated view" merges adjacent
        // same-status slots into contiguous blocks (e.g. four back-to-back
        // FREE 30-min slots come back as one 2h FREE block). Slots that only
        // partially overlap the window are included with their full range —
        // we deliberately do not clip block edges to [from, to), so the caller
        // sees the true extent of each block.
        return AvailabilityBlock.merge(slots);
    }
}
