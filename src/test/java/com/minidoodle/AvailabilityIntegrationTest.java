package com.minidoodle;

import com.minidoodle.dto.AvailabilityBlock;
import com.minidoodle.dto.BookMeetingRequest;
import com.minidoodle.dto.CreateSlotRequest;
import com.minidoodle.dto.CreateUserRequest;
import com.minidoodle.dto.SlotResponse;
import com.minidoodle.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AvailabilityIntegrationTest extends BaseIntegrationTest {

    @Test
    void availability_returnsAllSlotsInWindow() {
        UserResponse user = createUser();
        Instant base = Instant.now().plus(100, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);

        createSlot(user, base, 0);
        createSlot(user, base, 2);
        createSlot(user, base, 4);

        ResponseEntity<AvailabilityBlock[]> response = rest.getForEntity(
                "/api/v1/users/" + user.id() + "/availability"
                        + "?from=" + base.minus(1, ChronoUnit.HOURS)
                        + "&to=" + base.plus(6, ChronoUnit.HOURS),
                AvailabilityBlock[].class
        );

        assertThat(response.getBody()).hasSize(3);
    }

    @Test
    void availability_filteredByStatus() {
        UserResponse user = createUser();
        Instant base = Instant.now().plus(200, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);

        SlotResponse freeSlot = createSlot(user, base, 0);
        SlotResponse toBook = createSlot(user, base, 2);

        rest.postForEntity(
                "/api/v1/users/" + user.id() + "/slots/" + toBook.id() + "/meeting",
                new BookMeetingRequest("Busy meeting", null, List.of()),
                Object.class
        );

        ResponseEntity<AvailabilityBlock[]> freeOnly = rest.getForEntity(
                "/api/v1/users/" + user.id() + "/availability"
                        + "?from=" + base.minus(1, ChronoUnit.HOURS)
                        + "&to=" + base.plus(4, ChronoUnit.HOURS)
                        + "&status=FREE",
                AvailabilityBlock[].class
        );

        assertThat(freeOnly.getBody()).hasSize(1);
        assertThat(freeOnly.getBody()[0].slotIds()).containsExactly(freeSlot.id());
    }

    @Test
    void availability_adjacentSameStatusSlots_mergedIntoOneBlock() {
        UserResponse user = createUser();
        Instant base = Instant.now().plus(400, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);

        // Three back-to-back FREE slots: [0,1h) [1h,2h) [2h,3h)
        SlotResponse s1 = createSlot(user, base, 0);
        SlotResponse s2 = createSlot(user, base, 1);
        SlotResponse s3 = createSlot(user, base, 2);

        ResponseEntity<AvailabilityBlock[]> response = rest.getForEntity(
                "/api/v1/users/" + user.id() + "/availability"
                        + "?from=" + base.minus(1, ChronoUnit.HOURS)
                        + "&to=" + base.plus(4, ChronoUnit.HOURS),
                AvailabilityBlock[].class
        );

        assertThat(response.getBody()).hasSize(1);
        AvailabilityBlock block = response.getBody()[0];
        assertThat(block.startAt()).isEqualTo(base);
        assertThat(block.endAt()).isEqualTo(base.plus(3, ChronoUnit.HOURS));
        assertThat(block.slotIds()).containsExactly(s1.id(), s2.id(), s3.id());
    }

    @Test
    void availability_adjacentSlotsWithDifferentStatus_notMerged() {
        UserResponse user = createUser();
        Instant base = Instant.now().plus(500, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);

        createSlot(user, base, 0);
        SlotResponse toBook = createSlot(user, base, 1); // adjacent, becomes BUSY

        rest.postForEntity(
                "/api/v1/users/" + user.id() + "/slots/" + toBook.id() + "/meeting",
                new BookMeetingRequest("Blocker", null, List.of()),
                Object.class
        );

        ResponseEntity<AvailabilityBlock[]> response = rest.getForEntity(
                "/api/v1/users/" + user.id() + "/availability"
                        + "?from=" + base + "&to=" + base.plus(2, ChronoUnit.HOURS),
                AvailabilityBlock[].class
        );

        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody()[0].status().name()).isEqualTo("FREE");
        assertThat(response.getBody()[1].status().name()).isEqualTo("BUSY");
    }

    @Test
    void availability_partiallyOverlappingSlots_areIncluded() {
        UserResponse user = createUser();
        Instant base = Instant.now().plus(300, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        createSlot(user, base, 0); // starts at base, ends at base+1h

        // Query window starts at base+30min — slot partially overlaps
        ResponseEntity<AvailabilityBlock[]> response = rest.getForEntity(
                "/api/v1/users/" + user.id() + "/availability"
                        + "?from=" + base.plus(30, ChronoUnit.MINUTES)
                        + "&to=" + base.plus(2, ChronoUnit.HOURS),
                AvailabilityBlock[].class
        );

        assertThat(response.getBody()).hasSize(1);
    }

    private UserResponse createUser() {
        return rest.postForEntity(
                "/api/v1/users",
                new CreateUserRequest("avail-" + System.nanoTime() + "@test.com", "Avail User", "UTC"),
                UserResponse.class
        ).getBody();
    }

    private SlotResponse createSlot(UserResponse user, Instant base, int hourOffset) {
        Instant start = base.plus(hourOffset, ChronoUnit.HOURS);
        return rest.postForEntity(
                "/api/v1/users/" + user.id() + "/slots",
                new CreateSlotRequest(start, start.plus(1, ChronoUnit.HOURS), null),
                SlotResponse.class
        ).getBody();
    }
}
