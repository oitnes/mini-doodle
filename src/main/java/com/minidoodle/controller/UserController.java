package com.minidoodle.controller;

import com.minidoodle.domain.SlotStatus;
import com.minidoodle.dto.AvailabilityBlock;
import com.minidoodle.dto.CreateUserRequest;
import com.minidoodle.dto.UserResponse;
import com.minidoodle.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        return userService.createUser(request);
    }

    @GetMapping("/{userId}/availability")
    public List<AvailabilityBlock> getAvailability(
            @PathVariable UUID userId,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(required = false) SlotStatus status
    ) {
        return userService.getAvailability(userId, from, to, status);
    }
}
