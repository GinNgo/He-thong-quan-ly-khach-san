package com.hotel.dtos;

import jakarta.validation.constraints.Size;

public record PropertyApprovalNoteRequest(
        @Size(max = 500)
        String note) {

    public PropertyApprovalNoteRequest {
        note = note == null || note.isBlank() ? null : note.trim();
    }
}
