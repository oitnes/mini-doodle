package com.minidoodle.repository;

import com.minidoodle.domain.Meeting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MeetingRepository extends JpaRepository<Meeting, UUID> {

    boolean existsBySlot_Id(UUID slotId);

    Optional<Meeting> findByIdAndSlot_Calendar_User_Id(UUID meetingId, UUID userId);
}
