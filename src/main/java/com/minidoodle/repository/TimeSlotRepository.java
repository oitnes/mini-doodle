package com.minidoodle.repository;

import com.minidoodle.domain.SlotStatus;
import com.minidoodle.domain.TimeSlot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TimeSlotRepository extends JpaRepository<TimeSlot, UUID> {

    Optional<TimeSlot> findByIdAndCalendar_User_Id(UUID slotId, UUID userId);

    Page<TimeSlot> findByCalendar_User_Id(UUID userId, Pageable pageable);

    // Bulk updates bypass Hibernate's automatic @Version handling, so the
    // version is bumped explicitly: a concurrent transaction holding a stale
    // snapshot of this row must fail its optimistic-lock check instead of
    // silently overwriting the status written here.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE TimeSlot s SET s.status = :status, s.version = s.version + 1 WHERE s.id = :id")
    void updateStatusById(@Param("id") UUID id, @Param("status") SlotStatus status);

    @Query("""
            SELECT s FROM TimeSlot s
            WHERE s.calendar.id = :calendarId
              AND s.startAt < :to AND s.endAt > :from
            ORDER BY s.startAt
            """)
    List<TimeSlot> findInRange(
            @Param("calendarId") UUID calendarId,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    @Query("""
            SELECT s FROM TimeSlot s
            WHERE s.calendar.id = :calendarId
              AND s.startAt < :to AND s.endAt > :from
              AND s.status = :status
            ORDER BY s.startAt
            """)
    List<TimeSlot> findInRangeByStatus(
            @Param("calendarId") UUID calendarId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("status") SlotStatus status
    );
}
