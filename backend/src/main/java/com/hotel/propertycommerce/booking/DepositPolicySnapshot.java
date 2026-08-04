package com.hotel.propertycommerce.booking;

import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.propertycommerce.config.PropertyPaymentConfiguration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Immutable evidence of the property deposit policy used for a booking or payment attempt. */
public record DepositPolicySnapshot(
        Long propertyId,
        Long configurationId,
        long configurationVersion,
        PolicyType policyType,
        BigDecimal policyValue,
        VndMoney bookingTotal,
        VndMoney requiredDeposit) {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    public DepositPolicySnapshot {
        propertyId = requirePositiveId(propertyId, "propertyId");
        configurationId = requirePositiveId(configurationId, "configurationId");
        if (configurationVersion < 0) {
            throw invalid("configurationVersion", "Configuration version must not be negative.");
        }
        policyType = Objects.requireNonNull(policyType, "policyType must not be null");
        bookingTotal = Objects.requireNonNull(bookingTotal, "bookingTotal must not be null");
        if (bookingTotal.amount().signum() <= 0) {
            throw invalid("bookingTotal", "Booking total must be positive VND.");
        }

        policyValue = normalizePolicyValue(policyType, policyValue);
        VndMoney calculatedDeposit = calculate(policyType, policyValue, bookingTotal);
        requiredDeposit = Objects.requireNonNull(requiredDeposit, "requiredDeposit must not be null");
        if (calculatedDeposit.amount().compareTo(requiredDeposit.amount()) != 0) {
            throw invalid("requiredDeposit", "Required deposit does not match the snapshotted policy.");
        }
    }

    public static DepositPolicySnapshot capture(
            PropertyPaymentConfiguration configuration,
            BigDecimal authoritativeBookingTotal) {
        Objects.requireNonNull(configuration, "configuration must not be null");
        if (configuration.getHotel() == null) {
            throw new IllegalArgumentException("Payment configuration must belong to a property.");
        }
        if (authoritativeBookingTotal == null) {
            throw invalid("bookingTotal", "Booking total is required.");
        }

        VndMoney bookingTotal;
        try {
            bookingTotal = VndMoney.of(authoritativeBookingTotal);
        } catch (IllegalArgumentException | ArithmeticException exception) {
            throw new FinancialException(
                    FinancialErrorCode.INVALID_AMOUNT,
                    "Booking total must be a non-negative integer VND amount.",
                    Map.of("bookingTotal", "Must be an integer VND amount."),
                    null,
                    exception);
        }

        PolicyType policyType = PolicyType.from(configuration.getDepositPolicyType());
        BigDecimal policyValue = normalizePolicyValue(policyType, configuration.getDepositValue());
        VndMoney requiredDeposit = calculate(policyType, policyValue, bookingTotal);
        return new DepositPolicySnapshot(
                configuration.getHotel().getId(),
                configuration.getId(),
                configuration.getVersion(),
                policyType,
                policyValue,
                bookingTotal,
                requiredDeposit);
    }

    public static DepositPolicySnapshot reprice(
            DepositPolicySnapshot original,
            BigDecimal authoritativeBookingTotal) {
        Objects.requireNonNull(original, "original snapshot must not be null");
        if (authoritativeBookingTotal == null) {
            throw invalid("bookingTotal", "Booking total is required.");
        }
        VndMoney bookingTotal;
        try {
            bookingTotal = VndMoney.of(authoritativeBookingTotal);
        } catch (IllegalArgumentException | ArithmeticException exception) {
            throw invalid("bookingTotal", "Booking total must be an integer VND amount.", exception);
        }
        return new DepositPolicySnapshot(
                original.propertyId(),
                original.configurationId(),
                original.configurationVersion(),
                original.policyType(),
                original.policyValue(),
                bookingTotal,
                calculate(original.policyType(), original.policyValue(), bookingTotal));
    }

    public String currency() {
        return "VND";
    }

    private static BigDecimal normalizePolicyValue(PolicyType policyType, BigDecimal rawValue) {
        return switch (policyType) {
            case NONE -> {
                if (rawValue != null && rawValue.signum() != 0) {
                    throw invalid("policyValue", "No-deposit policy must have a zero value.");
                }
                yield BigDecimal.ZERO.setScale(0);
            }
            case FIXED -> {
                if (rawValue == null) {
                    throw invalid("policyValue", "Fixed deposit value is required.");
                }
                VndMoney amount;
                try {
                    amount = VndMoney.of(rawValue);
                } catch (IllegalArgumentException | ArithmeticException exception) {
                    throw invalid("policyValue", "Fixed deposit must be an integer VND amount.", exception);
                }
                if (amount.amount().signum() <= 0) {
                    throw invalid("policyValue", "Fixed deposit must be positive.");
                }
                yield amount.amount();
            }
            case PERCENTAGE -> {
                if (rawValue == null) {
                    throw invalid("policyValue", "Deposit percentage is required.");
                }
                BigDecimal percentage;
                try {
                    percentage = rawValue.setScale(0, RoundingMode.UNNECESSARY);
                } catch (ArithmeticException exception) {
                    throw invalid("policyValue", "Deposit percentage must be an integer.", exception);
                }
                if (percentage.compareTo(BigDecimal.ONE) < 0 || percentage.compareTo(ONE_HUNDRED) > 0) {
                    throw invalid("policyValue", "Deposit percentage must be between 1 and 100.");
                }
                yield percentage;
            }
        };
    }

    private static VndMoney calculate(PolicyType policyType, BigDecimal policyValue, VndMoney bookingTotal) {
        return switch (policyType) {
            case NONE -> VndMoney.zero();
            case FIXED -> VndMoney.of(policyValue.min(bookingTotal.amount()));
            case PERCENTAGE -> {
                try {
                    BigDecimal amount = bookingTotal.amount()
                            .multiply(policyValue)
                            .divide(ONE_HUNDRED, 0, RoundingMode.UNNECESSARY);
                    yield VndMoney.of(amount);
                } catch (ArithmeticException exception) {
                    throw invalid(
                            "bookingTotal",
                            "Percentage policy must resolve to an exact integer VND deposit.",
                            exception);
                }
            }
        };
    }

    private static Long requirePositiveId(Long value, String field) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(field + " must be a positive identifier.");
        }
        return value;
    }

    private static FinancialException invalid(String field, String message) {
        return invalid(field, message, null);
    }

    private static FinancialException invalid(String field, String message, Throwable cause) {
        return new FinancialException(
                FinancialErrorCode.INVALID_AMOUNT,
                message,
                Map.of(field, message),
                null,
                cause);
    }

    public enum PolicyType {
        NONE,
        FIXED,
        PERCENTAGE;

        public static PolicyType from(String value) {
            if (value == null || value.isBlank()) {
                return NONE;
            }
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Unsupported deposit policy: " + value, exception);
            }
        }
    }
}
