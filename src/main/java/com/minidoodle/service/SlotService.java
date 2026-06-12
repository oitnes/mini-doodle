package com.minidoodle.service;

import com.minidoodle.domain.Calendar;
import com.minidoodle.domain.SlotStatus;
import com.minidoodle.domain.TimeSlot;
import com.minidoodle.dto.CreateSlotRequest;
import com.minidoodle.dto.SlotResponse;
import com.minidoodle.dto.UpdateSlotRequest;
import com.minidoodle.exception.SlotModificationForbiddenException;
import com.minidoodle.exception.SlotNotFoundException;
import com.minidoodle.exception.UserNotFoundException;
import com.minidoodle.repository.CalendarRepository;
import com.minidoodle.repository.MeetingRepository;
import com.minidoodle.repository.TimeSlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SlotService {

    private final CalendarRepository calendarRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final MeetingRepository meetingRepository;

    @Transactional
    public SlotResponse createSlot(UUID userId, CreateSlotRequest request) {
        Calendar calendar = calendarRepository.findByUser_Id(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        Instant endAt = resolveEndAt(request);
        validateRange(request.startAt(), endAt);

        TimeSlot slot = new TimeSlot();
        slot.setCalendar(calendar);
        slot.setStartAt(request.startAt());
        slot.setEndAt(endAt);
        slot.setStatus(SlotStatus.FREE);
        timeSlotRepository.save(slot);
        return SlotResponse.from(slot);
    }

    @Transactional
    public SlotResponse updateSlot(UUID userId, UUID slotId, UpdateSlotRequest request) {
        TimeSlot slot = timeSlotRepository.findByIdAndCalendar_User_Id(slotId, userId)
                .orElseThrow(() -> new SlotNotFoundException(slotId));

        // ARCHITECTURE DECISION: BUSY alone does not lock a slot. A slot is
        // immutable only while a meeting is attached to it — the meeting is the
        // source of truth, and freeing its slot must go through meeting
        // cancellation so the two never disagree. A slot the user marked BUSY
        // by hand (no meeting) stays fully editable, including back to FREE;
        // otherwise manual BUSY would be a one-way door.
        if (meetingRepository.existsBySlot_Id(slotId)) {
            throw new SlotModificationForbiddenException(slotId);
        }
        if (request.startAt() != null) slot.setStartAt(request.startAt());
        if (request.endAt() != null) slot.setEndAt(request.endAt());
        if (request.status() != null) slot.setStatus(request.status());

        validateRange(slot.getStartAt(), slot.getEndAt());

        // slot is managed: dirty checking flushes the change and fires the
        // @Version check at commit — no explicit save() needed.
        return SlotResponse.from(slot);
    }

    @Transactional
    public void deleteSlot(UUID userId, UUID slotId) {
        TimeSlot slot = timeSlotRepository.findByIdAndCalendar_User_Id(slotId, userId)
                .orElseThrow(() -> new SlotNotFoundException(slotId));

        // Same rule as updateSlot: only a meeting protects a slot from deletion.
        if (meetingRepository.existsBySlot_Id(slotId)) {
            throw new SlotModificationForbiddenException(slotId);
        }
        timeSlotRepository.delete(slot);
    }

    @Transactional(readOnly = true)
    public SlotResponse getSlot(UUID userId, UUID slotId) {
        return timeSlotRepository.findByIdAndCalendar_User_Id(slotId, userId)
                .map(SlotResponse::from)
                .orElseThrow(() -> new SlotNotFoundException(slotId));
    }

    @Transactional(readOnly = true)
    public Page<SlotResponse> listSlots(UUID userId, Pageable pageable) {
        calendarRepository.findByUser_Id(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        return timeSlotRepository.findByCalendar_User_Id(userId, pageable)
                .map(SlotResponse::from);
    }

    private Instant resolveEndAt(CreateSlotRequest request) {
        if (request.endAt() != null && request.durationMinutes() != null) {
            throw new IllegalArgumentException("Provide either endAt or durationMinutes, not both");
        }
        if (request.endAt() != null) {
            return request.endAt();
        }
        if (request.durationMinutes() != null) {
            return request.startAt().plus(request.durationMinutes(), ChronoUnit.MINUTES);
        }
        throw new IllegalArgumentException("Either endAt or durationMinutes must be provided");
    }

    // Single home for the time-range invariant so create and update cannot
    // drift apart in rule or message.
    private static void validateRange(Instant startAt, Instant endAt) {
        if (!startAt.isBefore(endAt)) {
            throw new IllegalArgumentException("end must be after start");
        }
    }
}
