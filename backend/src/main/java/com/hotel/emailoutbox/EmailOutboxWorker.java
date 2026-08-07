package com.hotel.emailoutbox;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EmailOutboxWorker {

    private final EmailOutboxService emailOutboxService;

    public EmailOutboxWorker(EmailOutboxService emailOutboxService) {
        this.emailOutboxService = emailOutboxService;
    }

    @Scheduled(fixedDelayString = "${app.mail.outbox.scan-ms:30000}")
    public void dispatchDueMessages() {
        emailOutboxService.processDueBatch();
    }
}
