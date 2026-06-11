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
        slot.setStatus(SlotStatus.BUSY);
        timeSlotRepository.save(slot);  // version bump — concurrent loser throws OptimisticLockingFailureException

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
    public MeetingResponse getMeeting(UUID meetingId) {
        return meetingRepository.findById(meetingId)
                .map(MeetingResponse::from)
                .orElseThrow(() -> new MeetingNotFoundException(meetingId));
    }

    // ARCHITECTURE DECISION: cancellation is idempotent (DELETE of a missing
    // meeting is a 204, not a 404) and is not scoped to a user. The prototype
    // has no authentication, so ownership checks on this endpoint would only
    // pretend to be security; once authN exists, the caller's identity — not a
    // path parameter — must authorize the cancel.
    @Transactional
    public void cancelMeeting(UUID meetingId) {
        meetingRepository.findById(meetingId).ifPresent(meeting -> {
            UUID slotId = meeting.getSlot().getId();
            // Delete the meeting first (participants cascade at DB level).
            // Then use a bulk JPQL update to free the slot — avoids Hibernate
            // attempting to flush the dirty slot while the meeting entity is
            // still in the persistence context.
            meetingRepository.delete(meeting);
            timeSlotRepository.updateStatusById(slotId, SlotStatus.FREE);
        });
        // idempotent: no error if already cancelled/missing
    }

    private Participant buildParticipant(Meeting meeting, ParticipantRequest request) {
        Participant p = new Participant();
        p.setMeeting(meeting);
        p.setEmail(request.email());
        userRepository.findByEmail(request.email()).ifPresent(p::setUser);
        return p;
    }
}
