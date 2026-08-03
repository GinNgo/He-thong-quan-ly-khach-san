package com.hotel.notifications.preferences;

import java.util.Locale;

public enum NotificationEventClass {
    ACCOUNT_SECURITY(true, "Bao mat tai khoan"),
    BOOKING(true, "Dat phong"),
    PAYMENT(true, "Thanh toan"),
    REFUND(true, "Hoan tien"),
    INVOICE(true, "Hoa don"),
    SUPPORT(true, "Ho tro"),
    MARKETING(false, "Uu dai va tin tuc");

    private final boolean mandatory;
    private final String label;

    NotificationEventClass(boolean mandatory, String label) {
        this.mandatory = mandatory;
        this.label = label;
    }

    public boolean isMandatory() { return mandatory; }
    public String getLabel() { return label; }

    public static NotificationEventClass fromNotificationType(String type) {
        String normalized = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "BOOKING", "RESERVATION" -> BOOKING;
            case "PAYMENT" -> PAYMENT;
            case "REFUND" -> REFUND;
            case "INVOICE" -> INVOICE;
            case "CHAT", "SUPPORT" -> SUPPORT;
            case "MARKETING", "PROMOTION", "NEWS" -> MARKETING;
            default -> ACCOUNT_SECURITY;
        };
    }
}
