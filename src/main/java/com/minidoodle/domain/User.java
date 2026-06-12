package com.minidoodle.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    // Set at instantiation rather than via @CreationTimestamp so the value is
    // already present in the POST response (Hibernate fills @CreationTimestamp
    // only at flush, after the response DTO has been built).
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    // No inverse @OneToOne to Calendar: the inverse side of an optional
    // one-to-one cannot be lazy without bytecode enhancement, so it would add
    // one calendar SELECT to every User load (e.g. each participant-email
    // lookup during booking). CalendarRepository.findByUser_Id covers the
    // one place the link is needed.
}
