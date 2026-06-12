package com.minidoodle.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// @Size limits mirror the DB column widths so oversized input fails here as a
// 400 validation error instead of surfacing as a 409 constraint violation.
public record CreateUserRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 255) String displayName,
        @Size(max = 100) String timezone
) {}
