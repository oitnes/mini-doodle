package com.minidoodle.exception;

import java.util.UUID;

public class SlotModificationForbiddenException extends RuntimeException {
    public SlotModificationForbiddenException(UUID slotId) {
        super("Cannot modify a booked slot: " + slotId + ". Cancel the meeting first.");
    }
}
