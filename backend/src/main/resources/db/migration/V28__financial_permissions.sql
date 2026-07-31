DECLARE @systemModuleId BIGINT = (SELECT TOP 1 id FROM dbo.app_module WHERE code = 'SYSTEM' OR name = N'Hệ thống' ORDER BY id);
DECLARE @financeModuleId BIGINT = (SELECT TOP 1 id FROM dbo.app_module WHERE code = 'FINANCE' OR name = N'Tài chính' ORDER BY id);
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;

DECLARE @moduleId BIGINT = COALESCE(@financeModuleId, @systemModuleId);

IF @moduleId IS NOT NULL
BEGIN
    INSERT INTO dbo.app_function(code, name, url, icon, sort_order, module_id)
    SELECT source.code, source.name, source.url, source.icon, source.sort_order, @moduleId
    FROM (VALUES
        ('PROPERTY_PAYMENT_CONFIG', N'Cấu hình thanh toán cơ sở', '/management/payment-configuration', 'pi pi-wallet', 20),
        ('PROPERTY_PAYMENT_CONFIRM_MANUAL', N'Xác nhận chuyển khoản', '/management/payment-confirmations', 'pi pi-check-circle', 21),
        ('PROPERTY_REFUND', N'Hoàn tiền cơ sở', '/management/refunds', 'pi pi-replay', 22),
        ('RESERVATION_SERVICE', N'Dịch vụ lưu trú', '/management/reservation-services', 'pi pi-box', 23),
        ('RESERVATION_SURCHARGE', N'Phụ thu lưu trú', '/management/reservation-surcharges', 'pi pi-plus-circle', 24),
        ('RESERVATION_DEBT_OVERRIDE', N'Cho phép checkout còn nợ', '/management/checkout-overrides', 'pi pi-exclamation-triangle', 25),
        ('INVOICE_ADJUST', N'Điều chỉnh hóa đơn', '/management/invoice-adjustments', 'pi pi-file-edit', 26),
        ('PLATFORM_BILLING', N'Thanh toán gói hệ thống', '/management/subscription-billing', 'pi pi-credit-card', 27),
        ('PLATFORM_REFUND', N'Hoàn tiền gói hệ thống', '/admin/platform-refunds', 'pi pi-replay', 28),
        ('PLATFORM_REVENUE', N'Doanh thu nền tảng', '/admin/platform-revenue', 'pi pi-chart-line', 29),
        ('PAYMENT_READINESS', N'Độ sẵn sàng thanh toán', '/admin/payment-readiness', 'pi pi-shield', 30)
    ) source(code, name, url, icon, sort_order)
    WHERE NOT EXISTS (SELECT 1 FROM dbo.app_function existing WHERE existing.code = source.code);
END;

IF OBJECT_ID('dbo.app_function', 'U') IS NOT NULL
   AND OBJECT_ID('dbo.app_role', 'U') IS NOT NULL
   AND OBJECT_ID('dbo.app_role_permission', 'U') IS NOT NULL
BEGIN
    DECLARE @superAdminRoleId BIGINT = (SELECT TOP 1 id FROM dbo.app_role WHERE code = 'SUPER_ADMIN');
    IF @superAdminRoleId IS NOT NULL
    BEGIN
        INSERT INTO dbo.app_role_permission(role_id, function_id, action_mask)
        SELECT @superAdminRoleId, f.id, 63
        FROM dbo.app_function f
        WHERE f.code IN ('PLATFORM_BILLING','PLATFORM_REFUND','PLATFORM_REVENUE','PAYMENT_READINESS')
          AND NOT EXISTS (
              SELECT 1 FROM dbo.app_role_permission rp
              WHERE rp.role_id = @superAdminRoleId AND rp.function_id = f.id
          );
    END;
END;
