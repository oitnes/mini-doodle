package com.minidoodle.service;

import com.minidoodle.domain.Meeting;
import com.minidoodle.domain.Participant;
import com.minidoodle.domain.SlotStatus;
import com.minidoodle.domain.TimeSlot;
import com.minidoodle.dto.BookMeetingRequest;
import com.minidoodle.dto.MeetingResponse;
import com.minidoodle.dto.ParticipantRequest;
import com.minidoodle.exception.MeetingNotFoundException;
import com.minidoodle.exception.SlotAlreadyBookedException;
import com.minidoodle.exception.SlotNotFoundException;
import com.minidoodle.repository.MeetingRepository;
import com.minidoodle.repository.TimeSlotRepository;
import com.minidoodle.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MeetingService {

    private final TimeSlotRepository timeSlotRepository;
    private final MeetingRepository meetingRepository;
    private final UserRepository userRepository;

    @Transactional
    public MeetingResponse bookMeeting(UUID userId, UUID slotId, BookMeetingRequest request) {
        TimeSlot slot = timeSlotRepository.findByIdAndCalendar_User_Id(slotId, userId)
                .orElseThrow(() -> new SlotNotFoundException(slotId));

        if (slot.getStatus() != SlotStatus.FREE) {
            throw new SlotAlreadyBookedException(slotId);
        }
        // ARCHITECTURE DECISION: optimistic locking (@Version) instead of
        // SELECT ... FOR UPDATE. Booking contention on one slot is expected to
        // be rare, so we prefer no row locks held across the transaction and
        // let the losing writer fail fast with 409. If hot slots emerge
        // (e.g. a public booking page), switch this read to a pessimistic
        // lock. The UNIQUE constraint on meeting.slot_id is the DB-level
        // backstop should both defences ever be bypassed.
        //
        // slot is managed: dirty checking flushes this change at commit, where
        // the @Version check fires — the loser of a concurrent booking gets
        // OptimisticLockingFailureException (-> 409).
        slot.setStatus(SlotStatus.BUSY);

        Meeting meeting = new Meeting();
        meeting.setSlot(slot);
        meeting.setTitle(request.title());
        meeting.setDescription(request.description());

        List<Participant> participants = request.participants().stream()
                .map(p -> buildParticipant(meeting, p))
                .toList();
        meeting.setParticipants(participants);

        return MeetingResponse.from(meetingRepository.save(meeting));
    }

    @Transactional(readOnly = true)
    public MeetingResponse getMeeting(UUID userId, UUID meetingId) {
        return meetingRepository.findByIdAndSlot_Calendar_User_Id(meetingId, userId)
                .map(MeetingResponse::from)
                .orElseThrow(() -> new MeetingNotFoundException(meetingId));
    }

    // ARCHITECTURE DECISION: meeting access is scoped to the slot owner via
    // the path userId, exactly like the slot endpoints — without it, anyone
    // holding a meeting UUID could read participant emails or cancel another
    // user's meeting. There is still no authentication (the path userId is
    // trusted), but scoping keeps the authorization model uniform so adding
    // authN later means validating one identity, not auditing two patterns.
    // Cancellation stays idempotent: deleting a missing/foreign meeting is a
    // 204 no-op, not a 404.
    @Transactional
    public void cancelMeeting(UUID userId, UUID meetingId) {
        meetingRepository.findByIdAndSlot_Calendar_User_Id(meetingId, userId).ifPresent(meeting -> {
            UUID slotId = meeting.getSlot().getId();
            // Delete the meeting first (participants cascade at DB level).
            // Then use a bulk JPQL update to free the slot — avoids Hibernate
            // attempting to flush the dirty slot while the meeting entity is
            // still in the persistence context. The bulk update bumps
            // @Version itself (see TimeSlotRepository) so concurrent stale
            // writers cannot overwrite the freed status.
            meetingRepository.delete(meeting);
            timeSlotRepository.updateStatusById(slotId, SlotStatus.FREE);
        });
    }

    private Participant buildParticipant(Meeting meeting, ParticipantRequest request) {
        Participant p = new Participant();
        p.setMeeting(meeting);
        p.setEmail(request.email());
        userRepository.findByEmail(request.email()).ifPresent(p::setUser);
        return p;
    }
}
