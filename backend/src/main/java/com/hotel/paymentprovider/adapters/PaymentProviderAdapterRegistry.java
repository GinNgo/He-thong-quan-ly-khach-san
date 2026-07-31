package com.hotel.paymentprovider.adapters;

import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.paymentprovider.spi.PaymentProviderAdapter;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class PaymentProviderAdapterRegistry {

    private final Map<String, PaymentProviderAdapter> adapters;

    public PaymentProviderAdapterRegistry(List<PaymentProviderAdapter> adapters) {
        Map<String, PaymentProviderAdapter> indexed = new LinkedHashMap<>();
        for (PaymentProviderAdapter adapter : adapters) {
            String provider = normalize(adapter.provider());
            if (provider == null || indexed.putIfAbsent(provider, adapter) != null) {
                throw new IllegalStateException("Payment provider adapters must have unique provider names.");
            }
        }
        this.adapters = Map.copyOf(indexed);
    }

    public PaymentProviderAdapter require(String provider) {
        String normalized = normalize(provider);
        PaymentProviderAdapter adapter = normalized == null ? null : adapters.get(normalized);
        if (adapter == null) {
            throw new FinancialException(FinancialErrorCode.PROVIDER_UNAVAILABLE);
        }
        return adapter;
    }

    public boolean supports(String provider) {
        String normalized = normalize(provider);
        return normalized != null && adapters.containsKey(normalized);
    }

    private String normalize(String provider) {
        return provider == null || provider.isBlank() ? null : provider.trim().toUpperCase(Locale.ROOT);
    }
}
