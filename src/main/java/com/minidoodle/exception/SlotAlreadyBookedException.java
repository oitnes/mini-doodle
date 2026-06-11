package com.minidoodle.exception;

import java.util.UUID;

public class SlotAlreadyBookedException extends RuntimeException {
    public SlotAlreadyBookedException(UUID slotId) {
        super("Slot is already booked: " + slotId);
    }
}
