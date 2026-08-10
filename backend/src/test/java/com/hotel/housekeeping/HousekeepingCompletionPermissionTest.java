package com.hotel.housekeeping;

import com.hotel.controllers.ManagementPortalController;
import com.hotel.dtos.HousekeepingCommandRequest;
import com.hotel.security.ActionCode;
import com.hotel.security.FunctionCode;
import com.hotel.security.Permission;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HousekeepingCompletionPermissionTest {

    @Test
    void canonicalCompletionRequiresDedicatedTaskExecuteAction() throws Exception {
        Permission permission = HousekeepingController.class
                .getMethod("complete", Long.class, HousekeepingCommandRequest.class)
                .getAnnotation(Permission.class);

        assertThat(permission).isNotNull();
        assertThat(permission.function()).isEqualTo(FunctionCode.HOUSEKEEPING);
        assertThat(permission.action()).isEqualTo(ActionCode.TASK_EXECUTE);
    }

    @Test
    void legacyCompletionCannotBypassDedicatedTaskExecuteAction() throws Exception {
        Permission permission = ManagementPortalController.class
                .getMethod("completeHousekeeping", Long.class, HousekeepingCommandRequest.class)
                .getAnnotation(Permission.class);

        assertThat(permission).isNotNull();
        assertThat(permission.function()).isEqualTo(FunctionCode.HOUSEKEEPING);
        assertThat(permission.action()).isEqualTo(ActionCode.TASK_EXECUTE);
    }
}
