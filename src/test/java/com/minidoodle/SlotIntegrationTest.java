package com.minidoodle;

import com.minidoodle.dto.CreateSlotRequest;
import com.minidoodle.dto.CreateUserRequest;
import com.minidoodle.dto.SlotResponse;
import com.minidoodle.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class SlotIntegrationTest extends BaseIntegrationTest {

    @Test
    void createSlot_returnsCreated() {
        UserResponse user = createUser();

        Instant start = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        ResponseEntity<SlotResponse> response = rest.postForEntity(
                "/api/v1/users/" + user.id() + "/slots",
                new CreateSlotRequest(start, end, null),
                SlotResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().startAt()).isEqualTo(start);
        assertThat(response.getBody().createdAt()).isNotNull();
    }

    @Test
    void createSlot_withDuration_returnsCreated() {
        UserResponse user = createUser();
        Instant start = Instant.now().plus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);

        ResponseEntity<SlotResponse> response = rest.postForEntity(
                "/api/v1/users/" + user.id() + "/slots",
                new CreateSlotRequest(start, null, 30),
                SlotResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().endAt()).isEqualTo(start.plus(30, ChronoUnit.MINUTES));
    }

    @Test
    void createOverlappingSlot_returns409() {
        UserResponse user = createUser();
        Instant start = Instant.now().plus(10, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        rest.postForEntity("/api/v1/users/" + user.id() + "/slots",
                new CreateSlotRequest(start, end, null), SlotResponse.class);

        // Overlapping slot
        ResponseEntity<String> conflict = rest.postForEntity(
                "/api/v1/users/" + user.id() + "/slots",
                new CreateSlotRequest(start.plus(30, ChronoUnit.MINUTES), end.plus(30, ChronoUnit.MINUTES), null),
                String.class
        );

        assertThat(conflict.getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void deleteBookedSlot_returns409() {
        UserResponse user = createUser();
        Instant start = Instant.now().plus(20, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);

        SlotResponse slot = rest.postForEntity(
                "/api/v1/users/" + user.id() + "/slots",
                new CreateSlotRequest(start, start.plus(1, ChronoUnit.HOURS), null),
                SlotResponse.class
        ).getBody();

        rest.postForEntity(
                "/api/v1/users/" + user.id() + "/slots/" + slot.id() + "/meeting",
                new com.minidoodle.dto.BookMeetingRequest("Test", null, java.util.List.of()),
                com.minidoodle.dto.MeetingResponse.class
        );

        ResponseEntity<String> deleteResponse = rest.exchange(
                "/api/v1/users/" + user.id() + "/slots/" + slot.id(),
                HttpMethod.DELETE, null, String.class);
        assertThat(deleteResponse.getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void markSlotBusyThenFree_viaPatch() {
        UserResponse user = createUser();
        Instant start = Instant.now().plus(30, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);

        SlotResponse slot = rest.postForEntity(
                "/api/v1/users/" + user.id() + "/slots",
                new CreateSlotRequest(start, start.plus(1, ChronoUnit.HOURS), null),
                SlotResponse.class
        ).getBody();

        // Mark BUSY by hand (no meeting)
        SlotResponse busy = patchStatus(user, slot, "BUSY");
        assertThat(busy.status().name()).isEqualTo("BUSY");

        // A manually-busied slot must remain editable: mark it FREE again
        SlotResponse free = patchStatus(user, slot, "FREE");
        assertThat(free.status().name()).isEqualTo("FREE");
    }

    @Test
    void deleteManuallyBusiedSlot_succeeds() {
        UserResponse user = createUser();
        Instant start = Instant.now().plus(40, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);

        SlotResponse slot = rest.postForEntity(
                "/api/v1/users/" + user.id() + "/slots",
                new CreateSlotRequest(start, start.plus(1, ChronoUnit.HOURS), null),
                SlotResponse.class
        ).getBody();

        patchStatus(user, slot, "BUSY");

        ResponseEntity<Void> deleteResponse = rest.exchange(
                "/api/v1/users/" + user.id() + "/slots/" + slot.id(),
                HttpMethod.DELETE, null, Void.class);
        assertThat(deleteResponse.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void updateBookedSlot_returns409() {
        UserResponse user = createUser();
        Instant start = Instant.now().plus(50, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);

        SlotResponse slot = rest.postForEntity(
                "/api/v1/users/" + user.id() + "/slots",
                new CreateSlotRequest(start, start.plus(1, ChronoUnit.HOURS), null),
                SlotResponse.class
        ).getBody();

        rest.postForEntity(
                "/api/v1/users/" + user.id() + "/slots/" + slot.id() + "/meeting",
                new com.minidoodle.dto.BookMeetingRequest("Locked", null, java.util.List.of()),
                com.minidoodle.dto.MeetingResponse.class
        );

        ResponseEntity<String> patchResponse = rest.exchange(
                "/api/v1/users/" + user.id() + "/slots/" + slot.id(),
                HttpMethod.PATCH,
                new org.springframework.http.HttpEntity<>(java.util.Map.of("status", "FREE")),
                String.class);
        assertThat(patchResponse.getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void getSlot_returnsSlot() {
        UserResponse user = createUser();
        Instant start = Instant.now().plus(60, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);

        SlotResponse created = rest.postForEntity(
                "/api/v1/users/" + user.id() + "/slots",
                new CreateSlotRequest(start, start.plus(1, ChronoUnit.HOURS), null),
                SlotResponse.class
        ).getBody();

        ResponseEntity<SlotResponse> fetched = rest.getForEntity(
                "/api/v1/users/" + user.id() + "/slots/" + created.id(),
                SlotResponse.class);

        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody().id()).isEqualTo(created.id());
        assertThat(fetched.getBody().startAt()).isEqualTo(start);
    }

    @Test
    void createSlot_bothEndAtAndDuration_returns400() {
        UserResponse user = createUser();
        Instant start = Instant.now().plus(70, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);

        ResponseEntity<String> response = rest.postForEntity(
                "/api/v1/users/" + user.id() + "/slots",
                new CreateSlotRequest(start, start.plus(1, ChronoUnit.HOURS), 30),
                String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void createSlotForUnknownUser_returns404() {
        Instant start = Instant.now().plus(80, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);

        ResponseEntity<String> response = rest.postForEntity(
                "/api/v1/users/" + java.util.UUID.randomUUID() + "/slots",
                new CreateSlotRequest(start, start.plus(1, ChronoUnit.HOURS), null),
                String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void getUnknownSlot_returns404() {
        UserResponse user = createUser();

        ResponseEntity<String> response = rest.getForEntity(
                "/api/v1/users/" + user.id() + "/slots/" + java.util.UUID.randomUUID(), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void unknownSortProperty_returns400() {
        UserResponse user = createUser();

        ResponseEntity<String> response = rest.getForEntity(
                "/api/v1/users/" + user.id() + "/slots?sort=zzz", String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    private SlotResponse patchStatus(UserResponse user, SlotResponse slot, String status) {
        return rest.exchange(
                "/api/v1/users/" + user.id() + "/slots/" + slot.id(),
                HttpMethod.PATCH,
                new org.springframework.http.HttpEntity<>(java.util.Map.of("status", status)),
                SlotResponse.class
        ).getBody();
    }

    private UserResponse createUser() {
        return rest.postForEntity(
                "/api/v1/users",
                new CreateUserRequest("user-" + System.nanoTime() + "@test.com", "Test User", "UTC"),
                UserResponse.class
        ).getBody();
    }
}
