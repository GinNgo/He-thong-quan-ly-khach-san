package com.hotel.emailoutbox;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EmailOutboxWorkerTest {

    @Test
    void scheduledPollDelegatesToOutboxService() {
        EmailOutboxService service = mock(EmailOutboxService.class);
        new EmailOutboxWorker(service).dispatchDueMessages();
        verify(service).processDueBatch();
    }
}
