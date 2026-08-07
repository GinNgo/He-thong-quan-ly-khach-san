package com.hotel.housekeeping;

import com.hotel.dtos.HousekeepingAssigneeDTO;
import com.hotel.dtos.HousekeepingAssignRequest;
import com.hotel.dtos.HousekeepingCommandRequest;
import com.hotel.dtos.HousekeepingTaskDTO;
import com.hotel.security.ActionCode;
import com.hotel.security.FunctionCode;
import com.hotel.security.Permission;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/housekeeping")
@RequiredArgsConstructor
public class HousekeepingController {
    private final HousekeepingQueueService service;

    @GetMapping("/tasks")
    @Permission(function = FunctionCode.HOUSEKEEPING, action = ActionCode.VIEW)
    public ResponseEntity<List<HousekeepingTaskDTO>> list(
            @RequestParam Long propertyId,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(service.list(propertyId, status));
    }

    @GetMapping("/assignees")
    @Permission(function = FunctionCode.HOUSEKEEPING, action = ActionCode.VIEW)
    public ResponseEntity<List<HousekeepingAssigneeDTO>> assignees(@RequestParam Long propertyId) {
        return ResponseEntity.ok(service.assignees(propertyId));
    }

    @PostMapping("/tasks/{taskId}/claim")
    @Permission(function = FunctionCode.HOUSEKEEPING, action = ActionCode.UPDATE)
    public ResponseEntity<HousekeepingTaskDTO> claim(
            @PathVariable Long taskId,
            @RequestBody(required = false) HousekeepingCommandRequest request) {
        return ResponseEntity.ok(service.claim(taskId, request));
    }

    @PostMapping("/tasks/{taskId}/assign")
    @Permission(function = FunctionCode.HOUSEKEEPING, action = ActionCode.UPDATE)
    public ResponseEntity<HousekeepingTaskDTO> assign(
            @PathVariable Long taskId,
            @RequestBody HousekeepingAssignRequest request) {
        return ResponseEntity.ok(service.assign(taskId, request));
    }

    @PostMapping("/tasks/{taskId}/start")
    @Permission(function = FunctionCode.HOUSEKEEPING, action = ActionCode.UPDATE)
    public ResponseEntity<HousekeepingTaskDTO> start(
            @PathVariable Long taskId,
            @RequestBody(required = false) HousekeepingCommandRequest request) {
        return ResponseEntity.ok(service.start(taskId, request));
    }

    @PostMapping("/tasks/{taskId}/complete")
    @Permission(function = FunctionCode.HOUSEKEEPING, action = ActionCode.APPROVE)
    public ResponseEntity<HousekeepingTaskDTO> complete(
            @PathVariable Long taskId,
            @RequestBody(required = false) HousekeepingCommandRequest request) {
        return ResponseEntity.ok(service.complete(taskId, request));
    }
}
