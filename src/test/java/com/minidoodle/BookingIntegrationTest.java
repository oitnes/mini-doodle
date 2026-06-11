package com.minidoodle;

import com.minidoodle.dto.BookMeetingRequest;
import com.minidoodle.dto.CreateSlotRequest;
import com.minidoodle.dto.CreateUserRequest;
import com.minidoodle.dto.MeetingResponse;
import com.minidoodle.dto.SlotResponse;
import com.minidoodle.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class BookingIntegrationTest extends BaseIntegrationTest {

    @Test
    void bookFreeSlot_returnsCreated() {
        UserResponse user = createUser();
        SlotResponse slot = createSlot(user, 1);

        ResponseEntity<MeetingResponse> response = rest.postForEntity(
                "/api/v1/users/" + user.id() + "/slots/" + slot.id() + "/meeting",
                new BookMeetingRequest("Standup", "Daily standup", List.of()),
                MeetingResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().title()).isEqualTo("Standup");
    }

    @Test
    void bookAlreadyBusySlot_returns409() {
        UserResponse user = createUser();
        SlotResponse slot = createSlot(user, 2);

        rest.postForEntity(
                "/api/v1/users/" + user.id() + "/slots/" + slot.id() + "/meeting",
                new BookMeetingRequest("First", null, List.of()),
                MeetingResponse.class
        );

        ResponseEntity<String> second = rest.postForEntity(
                "/api/v1/users/" + user.id() + "/slots/" + slot.id() + "/meeting",
                new BookMeetingRequest("Second", null, List.of()),
                String.class
        );

        assertThat(second.getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void getMeeting_returnsBookedMeeting() {
        UserResponse user = createUser();
        SlotResponse slot = createSlot(user, 7);

        MeetingResponse created = rest.postForEntity(
                "/api/v1/users/" + user.id() + "/slots/" + slot.id() + "/meeting",
                new BookMeetingRequest("1:1", "Weekly sync", List.of(new com.minidoodle.dto.ParticipantRequest("guest@example.com"))),
                MeetingResponse.class
        ).getBody();

        ResponseEntity<MeetingResponse> fetched = rest.getForEntity(
                "/api/v1/meetings/" + created.id(), MeetingResponse.class);

        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody().title()).isEqualTo("1:1");
        assertThat(fetched.getBody().slotId()).isEqualTo(slot.id());
        assertThat(fetched.getBody().participants()).hasSize(1);
    }

    @Test
    void getMissingMeeting_returns404() {
        ResponseEntity<String> response = rest.getForEntity(
                "/api/v1/meetings/" + java.util.UUID.randomUUID(), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void cancelMeeting_freesSlot() {
        UserResponse user = createUser();
        SlotResponse slot = createSlot(user, 3);

        MeetingResponse meeting = rest.postForEntity(
                "/api/v1/users/" + user.id() + "/slots/" + slot.id() + "/meeting",
                new BookMeetingRequest("Retro", null, List.of()),
                MeetingResponse.class
        ).getBody();

        rest.delete("/api/v1/meetings/" + meeting.id());

        // Slot should be bookable again
        ResponseEntity<MeetingResponse> rebook = rest.postForEntity(
                "/api/v1/users/" + user.id() + "/slots/" + slot.id() + "/meeting",
                new BookMeetingRequest("Retro 2", null, List.of()),
                MeetingResponse.class
        );
        assertThat(rebook.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void cancelMeeting_idempotent() {
        UserResponse user = createUser();
        SlotResponse slot = createSlot(user, 4);

        MeetingResponse meeting = rest.postForEntity(
                "/api/v1/users/" + user.id() + "/slots/" + slot.id() + "/meeting",
                new BookMeetingRequest("Review", null, List.of()),
                MeetingResponse.class
        ).getBody();

        rest.delete("/api/v1/meetings/" + meeting.id());
        // Second cancel should not error
        rest.delete("/api/v1/meetings/" + meeting.id());
    }

    @Test
    void concurrentBooking_exactlyOneWins() throws InterruptedException {
        UserResponse user = createUser();
        SlotResponse slot = createSlot(user, 5);

        int threadCount = 8;
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            new Thread(() -> {
                try {
                    startGun.await();
                    ResponseEntity<MeetingResponse> r = rest.postForEntity(
                            "/api/v1/users/" + user.id() + "/slots/" + slot.id() + "/meeting",
                            new BookMeetingRequest("Race " + idx, null, List.of()),
                            MeetingResponse.class
                    );
                    if (r.getStatusCode().is2xxSuccessful()) successes.incrementAndGet();
                    else failures.incrementAndGet();
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    done.countDown();
                }
            }).start();
        }

        startGun.countDown();
        done.await();

        assertThat(successes.get()).isEqualTo(1);
        assertThat(failures.get()).isEqualTo(threadCount - 1);
    }

    private UserResponse createUser() {
        return rest.postForEntity(
                "/api/v1/users",
                new CreateUserRequest("booking-" + System.nanoTime() + "@test.com", "Booker", "UTC"),
                UserResponse.class
        ).getBody();
    }

    private SlotResponse createSlot(UserResponse user, int hourOffset) {
        Instant start = Instant.now().plus(hourOffset * 10L, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        return rest.postForEntity(
                "/api/v1/users/" + user.id() + "/slots",
                new CreateSlotRequest(start, start.plus(1, ChronoUnit.HOURS), null),
                SlotResponse.class
        ).getBody();
    }
}
