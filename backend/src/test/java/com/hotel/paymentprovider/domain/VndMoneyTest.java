package com.hotel.paymentprovider.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VndMoneyTest {

    @Test
    void acceptsWholeVndAndAddsExactly() {
        VndMoney total = VndMoney.of(new BigDecimal("1000")).add(VndMoney.of(250));
        assertEquals(new BigDecimal("1250"), total.amount());
    }

    @Test
    void rejectsFractionalVnd() {
        assertThrows(ArithmeticException.class, () -> VndMoney.of(new BigDecimal("1000.5")));
    }

    @Test
    void rejectsNegativeAmount() {
        assertThrows(IllegalArgumentException.class, () -> VndMoney.of(-1));
    }
}
