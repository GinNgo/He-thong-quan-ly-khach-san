package com.hotel.controllers;

import com.hotel.emailoutbox.EmailOutboxDtos;
import com.hotel.emailoutbox.EmailOutboxService;
import com.hotel.security.ActionCode;
import com.hotel.security.FunctionCode;
import com.hotel.security.Permission;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/email-outbox")
public class EmailOutboxController {

    private final EmailOutboxService emailOutboxService;

    @GetMapping("/failures")
    @Permission(function = FunctionCode.AUDIT_LOG, action = ActionCode.VIEW)
    public Page<EmailOutboxDtos.Failure> failures(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return emailOutboxService.failures(PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Order.desc("failedAt"), Sort.Order.desc("id"))));
    }

    @GetMapping("/{id}/attempts")
    @Permission(function = FunctionCode.AUDIT_LOG, action = ActionCode.VIEW)
    public List<EmailOutboxDtos.DeliveryAttempt> attempts(@PathVariable Long id) {
        return emailOutboxService.attempts(id);
    }

    /** Manual retry is deliberately guarded by AUDIT_LOG UPDATE, not merely queue visibility. */
    @PostMapping("/{id}/retry")
    @Permission(function = FunctionCode.AUDIT_LOG, action = ActionCode.UPDATE)
    public EmailOutboxDtos.Failure retry(@PathVariable Long id) {
        return emailOutboxService.manualRetry(id);
    }

    @PostMapping("/{id}/bounce")
    @Permission(function = FunctionCode.AUDIT_LOG, action = ActionCode.UPDATE)
    public EmailOutboxDtos.Failure bounce(@PathVariable Long id,
                                          @RequestBody(required = false) BounceRequest request) {
        return emailOutboxService.markBounced(id, request == null ? null : request.errorCode());
    }

    public record BounceRequest(String errorCode) {
    }
}
