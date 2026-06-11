package com.minidoodle.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ARCHITECTURE DECISION: per the task, "Calendar" exists only in the domain —
 * it is never a REST resource. One calendar is created automatically per user
 * (1:1 today) and acts as the container slots attach to. Keeping it as a
 * separate table (rather than hanging slots off the user directly) leaves
 * room for multiple calendars per user (work/private) without a schema break.
 */
@Entity
@Table(name = "calendar")
@Getter @Setter @NoArgsConstructor
public class Calendar {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Display-only preference. All instants are stored and compared in UTC;
    // clients localize using this value. Nothing server-side depends on it.
    @Column(nullable = false)
    private String timezone;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "calendar", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TimeSlot> slots = new ArrayList<>();
}
