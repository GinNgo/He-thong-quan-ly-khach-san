package com.hotel.emailoutbox;

public interface EmailDeliveryPort {
    DeliveryResult deliver(EmailOutboxMessage message);

    record DeliveryResult(EmailDeliveryOutcome outcome, String errorCode, String providerMessageId) {
        public static DeliveryResult sent(String providerMessageId) {
            return new DeliveryResult(EmailDeliveryOutcome.SENT, null, providerMessageId);
        }

        public static DeliveryResult failed(String errorCode) {
            return new DeliveryResult(EmailDeliveryOutcome.FAILED, errorCode, null);
        }

        public static DeliveryResult bounced(String errorCode) {
            return new DeliveryResult(EmailDeliveryOutcome.BOUNCED, errorCode, null);
        }
    }
}
