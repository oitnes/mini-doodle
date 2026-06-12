package com.minidoodle.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "time_slot")
@Getter @Setter @NoArgsConstructor
public class TimeSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calendar_id", nullable = false)
    private Calendar calendar;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SlotStatus status = SlotStatus.FREE;

    // Optimistic lock for the FREE -> BUSY transition during booking: two
    // concurrent bookings of the same slot both read the same version, and
    // the second commit fails with OptimisticLockingFailureException (-> 409).
    // Every write to this row — including the bulk free-the-slot update in
    // cancelMeeting — must bump the version, or concurrent holders of a stale
    // snapshot would pass the version check and overwrite fresh state.
    @Version
    private Long version;

    // Set at instantiation (not @CreationTimestamp) so creation responses
    // carry the value; see the same comment in User.
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    // Deliberately NO inverse @OneToOne to Meeting: Hibernate cannot lazy-load
    // the inverse side of an optional one-to-one without bytecode enhancement,
    // so the mapping would cost one extra SELECT per loaded slot (N+1 on every
    // list/availability query). Code that needs the link queries
    // MeetingRepository by slot id instead.
}
