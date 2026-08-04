package com.hotel.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class MaintenanceWorkOrderDTO {
    private Long id;
    private Long propertyId;
    @NotNull private Long roomId;
    private String roomNumber;
    @NotBlank @Size(max = 1000) private String reason;
    @NotBlank @Size(max = 20) private String priority;
    private Long assigneeUserId;
    private LocalDateTime scheduledStart;
    private LocalDateTime scheduledEnd;
    private String status;
    @Size(max = 2000) private String resolutionNote;
    private boolean bookingImpact;
    private Long version;
    private List<HistoryItem> history = List.of();

    public record HistoryItem(String fromStatus, String toStatus, String reason, LocalDateTime createdAt) {}
}
