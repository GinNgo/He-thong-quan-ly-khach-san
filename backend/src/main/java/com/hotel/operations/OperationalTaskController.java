package com.hotel.operations;

import com.hotel.security.ActionCode;
import com.hotel.security.FunctionCode;
import com.hotel.security.Permission;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/management/tasks")
@RequiredArgsConstructor
public class OperationalTaskController {
    private final OperationalTaskService service;

    @GetMapping
    @Permission(function = FunctionCode.OPERATIONAL_TASK, action = ActionCode.VIEW)
    public ResponseEntity<List<OperationalTaskService.OperationalTaskView>> list(
            @RequestParam Long hotelId,
            @RequestParam(required = false) OperationalTask.Status status) {
        return ResponseEntity.ok(service.list(hotelId, status));
    }

    @PostMapping("/{taskId}/claim")
    @Permission(function = FunctionCode.OPERATIONAL_TASK, action = ActionCode.TASK_EXECUTE)
    public ResponseEntity<OperationalTaskService.OperationalTaskView> claim(
            @PathVariable Long taskId, @RequestParam long expectedVersion) {
        return ResponseEntity.ok(service.claim(taskId, expectedVersion));
    }

    @PostMapping("/{taskId}/execute")
    @Permission(function = FunctionCode.OPERATIONAL_TASK, action = ActionCode.TASK_EXECUTE)
    public ResponseEntity<OperationalTaskService.OperationalTaskView> execute(
            @PathVariable Long taskId, @RequestBody OperationalTaskService.ExecuteCommand command) {
        return ResponseEntity.ok(service.execute(taskId, command));
    }

    @PostMapping("/{taskId}/reassign")
    @Permission(function = FunctionCode.OPERATIONAL_TASK, action = ActionCode.APPROVE)
    public ResponseEntity<OperationalTaskService.OperationalTaskView> reassign(
            @PathVariable Long taskId, @RequestBody OperationalTaskService.ReassignCommand command) {
        return ResponseEntity.ok(service.reassign(taskId, command));
    }

    @PostMapping("/{taskId}/block")
    @Permission(function = FunctionCode.OPERATIONAL_TASK, action = ActionCode.APPROVE)
    public ResponseEntity<OperationalTaskService.OperationalTaskView> block(
            @PathVariable Long taskId, @RequestBody OperationalTaskService.BlockCommand command) {
        return ResponseEntity.ok(service.block(taskId, command));
    }
}
