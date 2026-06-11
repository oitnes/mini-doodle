package com.minidoodle.exception;

import java.util.UUID;

public class SlotNotFoundException extends RuntimeException {
    public SlotNotFoundException(UUID id) {
        super("Time slot not found: " + id);
    }
}
