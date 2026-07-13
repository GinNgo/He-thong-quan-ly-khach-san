package com.hotel.services;

import com.hotel.entities.*;
import com.hotel.repositories.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionBillingService {

    private final SubscriptionOrderRepository orderRepository;
    private final SubscriptionPaymentRepository paymentRepository;
    private final AccountSubscriptionRepository accountSubscriptionRepository;
    private final SubscriptionHistoryRepository historyRepository;
    private final SoftwareContractRepository contractRepository;
    private final SubscriptionPlanRepository planRepository;
    private final UserRepository userRepository;

    @Transactional
    public SubscriptionOrder createOrder(Long userId, Long planId, String billingType) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        SubscriptionPlan plan = planRepository.findById(planId).orElseThrow(() -> new RuntimeException("Plan not found"));

        SubscriptionOrder order = new SubscriptionOrder();
        order.setOrderCode("SUB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        order.setUser(user);
        order.setPlan(plan);
        order.setBillingType(billingType);
        
        BigDecimal price = plan.getPrice() != null ? plan.getPrice() : BigDecimal.ZERO;
        order.setSubtotal(price);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setTaxAmount(BigDecimal.ZERO);
        order.setTotalAmount(price);
        order.setStatus("PENDING_PAYMENT");
        order.setExpiresAt(LocalDateTime.now().plusDays(1));

        return orderRepository.save(order);
    }

    @Transactional
    public void processPaymentSuccess(String orderCode, String transactionCode, String paymentMethod, BigDecimal amount) {
        SubscriptionOrder order = orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (!"PENDING_PAYMENT".equals(order.getStatus())) {
            throw new RuntimeException("Order is not in pending payment state");
        }

        // 1. Mark order PAID
        order.setStatus("PAID");
        orderRepository.save(order);

        // Record payment
        SubscriptionPayment payment = new SubscriptionPayment();
        payment.setOrder(order);
        payment.setPaymentMethod(paymentMethod);
        payment.setAmount(amount);
        payment.setTransactionCode(transactionCode);
        payment.setPaymentStatus("SUCCESS");
        payment.setPaidAt(LocalDateTime.now());
        paymentRepository.save(payment);

        // 2. Create or Update AccountSubscription
        AccountSubscription subscription = new AccountSubscription();
        subscription.setUser(order.getUser());
        subscription.setPlan(order.getPlan());
        subscription.setStartAt(LocalDateTime.now());
        subscription.setIsLifetime(order.getPlan().getIsLifetime());
        if (!subscription.getIsLifetime()) {
            subscription.setEndAt(LocalDateTime.now().plusYears(1)); // Assuming YEARLY for now
        }
        subscription.setStatus("ACTIVE");
        accountSubscriptionRepository.save(subscription);

        // 3. Create SubscriptionHistory
        SubscriptionHistory history = new SubscriptionHistory();
        history.setAccountSubscription(subscription);
        history.setPlan(order.getPlan());
        history.setActionType("ACTIVATED");
        history.setNote("Activated via order " + orderCode);
        historyRepository.save(history);

        // 4. Create SoftwareContract
        SoftwareContract contract = new SoftwareContract();
        contract.setContractNo("CTR-" + orderCode);
        contract.setUser(order.getUser());
        contract.setPlan(order.getPlan());
        contract.setOrder(order);
        contract.setContractType(subscription.getIsLifetime() ? "LIFETIME_PURCHASE" : "YEARLY_RENTAL");
        contract.setStartDate(subscription.getStartAt());
        contract.setEndDate(subscription.getEndAt());
        contract.setIsLifetime(subscription.getIsLifetime());
        contract.setContractValue(order.getTotalAmount());
        contract.setStatus("ACTIVE");
        contract.setSignedAt(LocalDateTime.now());
        contractRepository.save(contract);

        // 5. Mark order ACTIVATED
        order.setStatus("ACTIVATED");
        orderRepository.save(order);

        log.info("Successfully processed payment and activated subscription for order {}", orderCode);
    }
}
