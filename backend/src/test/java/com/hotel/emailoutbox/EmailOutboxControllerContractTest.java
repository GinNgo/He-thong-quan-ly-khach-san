package com.hotel.emailoutbox;

import com.hotel.controllers.EmailOutboxController;
import com.hotel.security.ActionCode;
import com.hotel.security.FunctionCode;
import com.hotel.security.Permission;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmailOutboxControllerContractTest {

    @Test
    void queueReadAndManualRetryUseSeparateAuditPermissions() throws Exception {
        Permission read = EmailOutboxController.class.getDeclaredMethod("failures", int.class, int.class)
                .getAnnotation(Permission.class);
        Permission retry = EmailOutboxController.class.getDeclaredMethod("retry", Long.class)
                .getAnnotation(Permission.class);

        assertEquals(FunctionCode.AUDIT_LOG, read.function());
        assertEquals(ActionCode.VIEW, read.action());
        assertEquals(FunctionCode.AUDIT_LOG, retry.function());
        assertEquals(ActionCode.UPDATE, retry.action());
    }
}
