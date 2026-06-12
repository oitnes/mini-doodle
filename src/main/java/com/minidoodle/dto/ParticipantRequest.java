package com.minidoodle.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ParticipantRequest(@NotBlank @Email @Size(max = 255) String email) {}
