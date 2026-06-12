package com.minidoodle;

import com.minidoodle.dto.CreateUserRequest;
import com.minidoodle.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class UserIntegrationTest extends BaseIntegrationTest {

    @Test
    void createUser_returnsCreated() {
        ResponseEntity<UserResponse> response = rest.postForEntity(
                "/api/v1/users",
                new CreateUserRequest(unique() + "@test.com", "Alice", "Europe/Warsaw"),
                UserResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().createdAt()).isNotNull();
    }

    @Test
    void invalidTimezone_returns400() {
        ResponseEntity<String> response = rest.postForEntity(
                "/api/v1/users",
                new CreateUserRequest(unique() + "@test.com", "Alice", "Mars/OlympusMons"),
                String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void oversizedDisplayName_returns400() {
        ResponseEntity<String> response = rest.postForEntity(
                "/api/v1/users",
                new CreateUserRequest(unique() + "@test.com", "x".repeat(300), "UTC"),
                String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void malformedJson_returnsSharedErrorShape() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = rest.postForEntity(
                "/api/v1/users", new HttpEntity<>("{bad json", headers), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        // The documented error contract: framework-raised 4xxs must use the
        // same ErrorResponse shape as domain errors.
        assertThat(response.getBody()).contains("\"message\"").contains("\"status\":400");
    }

    @Test
    void nonUuidPathVariable_returnsSharedErrorShape() {
        ResponseEntity<String> response = rest.getForEntity("/api/v1/users/not-a-uuid/slots", String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).contains("\"message\"");
    }

    private static String unique() {
        return "user-" + System.nanoTime();
    }
}
