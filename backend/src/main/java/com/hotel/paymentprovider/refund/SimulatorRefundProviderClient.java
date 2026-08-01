package com.hotel.paymentprovider.refund;

import com.hotel.paymentprovider.config.PaymentEnvironmentGuard.PaymentEnvironment;
import org.springframework.stereotype.Component;

@Component
public class SimulatorRefundProviderClient implements RefundProviderClient {

    @Override
    public String provider() {
        return "SIMULATOR";
    }

    @Override
    public PreparedRefund prepare(PrepareRefund request) {
        if (request.environment() != PaymentEnvironment.SIMULATOR) {
            throw new IllegalArgumentException("The simulator refund client only supports SIMULATOR mode.");
        }
        return new PreparedRefund(request.reference(), true, "PENDING_SIMULATOR_CALLBACK");
    }
}
