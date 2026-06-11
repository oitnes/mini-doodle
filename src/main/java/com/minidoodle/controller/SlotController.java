package com.minidoodle.controller;

import com.minidoodle.dto.CreateSlotRequest;
import com.minidoodle.dto.SlotResponse;
import com.minidoodle.dto.UpdateSlotRequest;
import com.minidoodle.service.SlotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/{userId}/slots")
@RequiredArgsConstructor
public class SlotController {

    private final SlotService slotService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SlotResponse createSlot(
            @PathVariable UUID userId,
            @Valid @RequestBody CreateSlotRequest request
    ) {
        return slotService.createSlot(userId, request);
    }

    @GetMapping
    public Page<SlotResponse> listSlots(
            @PathVariable UUID userId,
            @PageableDefault(size = 50, sort = "startAt") Pageable pageable
    ) {
        return slotService.listSlots(userId, pageable);
    }

    @GetMapping("/{slotId}")
    public SlotResponse getSlot(
            @PathVariable UUID userId,
            @PathVariable UUID slotId
    ) {
        return slotService.getSlot(userId, slotId);
    }

    @PatchMapping("/{slotId}")
    public SlotResponse updateSlot(
            @PathVariable UUID userId,
            @PathVariable UUID slotId,
            @RequestBody UpdateSlotRequest request
    ) {
        return slotService.updateSlot(userId, slotId, request);
    }

    @DeleteMapping("/{slotId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSlot(
            @PathVariable UUID userId,
            @PathVariable UUID slotId
    ) {
        slotService.deleteSlot(userId, slotId);
    }
}
