package com.hotel.controllers;

import com.hotel.security.ActionCode;
import com.hotel.security.FunctionCode;
import com.hotel.security.Permission;
import com.hotel.services.OperationalExportService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OperationalExportControllerTest {
    @Test
    void endpointRequiresReportExportPermission() throws Exception {
        Permission permission = OperationalExportController.class.getMethod("export", Long.class,
                OperationalExportService.Dataset.class, String.class, LocalDate.class, LocalDate.class)
                .getAnnotation(Permission.class);
        assertEquals(FunctionCode.REPORT, permission.function());
        assertEquals(ActionCode.EXPORT, permission.action());
    }
}
