package com.hotel.platformbilling.reporting;

import com.hotel.paymentprovider.reporting.RevenueReportModels.FinancialContext;
import com.hotel.paymentprovider.reporting.RevenueReportModels.NormalizedFilters;
import com.hotel.platformbilling.order.PlatformSubscriptionOrderRepository;
import com.hotel.platformbilling.order.SubscriptionOrder;
import com.hotel.platformbilling.payment.PlatformFinancialTransaction;
import com.hotel.platformbilling.payment.PlatformPaymentAttempt;
import com.hotel.platformbilling.subscription.SubscriptionEntitlement;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

/** Reads system-owned Platform Billing evidence; no property tenant predicate is used. */
@Repository
@Transactional(readOnly = true)
public class PlatformRevenueRepository {

    private final EntityManager entityManager;

    public PlatformRevenueRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public PlatformRevenueSource load(NormalizedFilters filters) {
        QueryScope scope = QueryScope.from(filters);
        return new PlatformRevenueSource(
                transactions(scope), attempts(scope), orders(scope), entitlements(scope));
    }

    private List<TransactionSource> transactions(QueryScope scope) {
        StringBuilder jpql = new StringBuilder("""
                select transaction
                from PlatformFinancialTransaction transaction
                where transaction.occurredAt >= :fromInclusive
                  and transaction.occurredAt < :toExclusive
                """);
        appendPaymentFilters(jpql, "transaction", scope);
        if (scope.planCode() != null) {
            jpql.append(" and transaction.order.planCode = :planCode");
        }
        jpql.append(" order by transaction.occurredAt, transaction.id");
        TypedQuery<PlatformFinancialTransaction> query = entityManager.createQuery(
                jpql.toString(), PlatformFinancialTransaction.class);
        bindDates(query, scope);
        bindPaymentFilters(query, scope);
        if (scope.planCode() != null) {
            query.setParameter("planCode", scope.planCode());
        }
        return query.getResultList().stream().map(transaction -> new TransactionSource(
                transaction.getPublicId(),
                transaction.getOccurredAt(),
                transaction.getTransactionType(),
                transaction.getDirection(),
                transaction.getAmount(),
                transaction.getMethod(),
                transaction.getProvider(),
                transaction.getOrder().getPublicId(),
                transaction.getOrder().getOrderCode(),
                transaction.getOrder().getPlanCode(),
                transaction.getOrder().getPlanName(),
                transaction.getOrder().getBillingPeriod(),
                transaction.getOrder().getOperation(),
                transaction.getOrder().getStatus(),
                transaction.getAttempt() == null ? null : transaction.getAttempt().getId(),
                transaction.getOriginalTransaction() == null
                        ? null : transaction.getOriginalTransaction().getPublicId())).toList();
    }

    private List<AttemptSource> attempts(QueryScope scope) {
        StringBuilder jpql = new StringBuilder("""
                select attempt
                from PlatformPaymentAttempt attempt
                where attempt.createdAt >= :fromInclusive
                  and attempt.createdAt < :toExclusive
                """);
        appendPaymentFilters(jpql, "attempt", scope);
        if (scope.planCode() != null) {
            jpql.append(" and attempt.order.planCode = :planCode");
        }
        jpql.append(" order by attempt.createdAt, attempt.id");
        TypedQuery<PlatformPaymentAttempt> query = entityManager.createQuery(
                jpql.toString(), PlatformPaymentAttempt.class);
        bindDates(query, scope);
        bindPaymentFilters(query, scope);
        if (scope.planCode() != null) {
            query.setParameter("planCode", scope.planCode());
        }
        return query.getResultList().stream().map(attempt -> new AttemptSource(
                attempt.getId(),
                attempt.getPublicId(),
                attempt.getCreatedAt(),
                attempt.getStatus(),
                attempt.getProvider(),
                attempt.getMethod(),
                attempt.getExpectedAmount(),
                attempt.getOrder().getPublicId(),
                attempt.getOrder().getPlanCode())).toList();
    }

    private List<OrderSource> orders(QueryScope scope) {
        StringBuilder jpql = new StringBuilder("""
                select orders
                from PlatformSubscriptionOrder orders
                where orders.createdAt >= :fromInclusive
                  and orders.createdAt < :toExclusive
                """);
        if (scope.planCode() != null) {
            jpql.append(" and orders.planCode = :planCode");
        }
        jpql.append(" order by orders.createdAt, orders.id");
        TypedQuery<SubscriptionOrder> query = entityManager.createQuery(
                jpql.toString(), SubscriptionOrder.class);
        bindDates(query, scope);
        if (scope.planCode() != null) {
            query.setParameter("planCode", scope.planCode());
        }
        return query.getResultList().stream().map(order -> new OrderSource(
                order.getId(), order.getPublicId(), order.getCreatedAt(), order.getPrice(),
                order.getOperation(), order.getStatus(), order.getPlanCode(), order.getPlanName(),
                order.getBillingPeriod(), order.getTargetHotel().getId())).toList();
    }

    private List<EntitlementSource> entitlements(QueryScope scope) {
        StringBuilder jpql = new StringBuilder("""
                select entitlement
                from SubscriptionEntitlement entitlement
                """);
        if (scope.planCode() != null) {
            jpql.append(" where entitlement.plan.code = :planCode");
        }
        jpql.append(" order by entitlement.updatedAt, entitlement.id");
        TypedQuery<SubscriptionEntitlement> query = entityManager.createQuery(
                jpql.toString(), SubscriptionEntitlement.class);
        if (scope.planCode() != null) {
            query.setParameter("planCode", scope.planCode());
        }
        return query.getResultList().stream().map(entitlement -> new EntitlementSource(
                entitlement.getId(), entitlement.getUpdatedAt(), entitlement.getStatus(),
                entitlement.getPlan().getCode(), entitlement.getPlan().getNameVi(),
                entitlement.getEffectiveFrom(), entitlement.getEffectiveUntil(),
                entitlement.isLifetime(), entitlement.getTargetHotel().getId())).toList();
    }

    private void appendPaymentFilters(StringBuilder jpql, String alias, QueryScope scope) {
        if (scope.provider() != null) {
            jpql.append(" and ").append(alias).append(".provider = :provider");
        }
        if (scope.method() != null) {
            jpql.append(" and ").append(alias).append(".method = :method");
        }
        if (scope.transactionType() != null && "transaction".equals(alias)) {
            jpql.append(" and ").append(alias).append(".transactionType = :transactionType");
        }
    }

    private void bindDates(jakarta.persistence.Query query, QueryScope scope) {
        query.setParameter("fromInclusive", scope.fromInclusive());
        query.setParameter("toExclusive", scope.toExclusive());
    }

    private void bindPaymentFilters(jakarta.persistence.Query query, QueryScope scope) {
        if (scope.provider() != null) {
            query.setParameter("provider", scope.provider());
        }
        if (scope.method() != null) {
            query.setParameter("method", scope.method());
        }
        if (scope.transactionType() != null && query.getParameters().stream()
                .anyMatch(parameter -> "transactionType".equals(parameter.getName()))) {
            query.setParameter("transactionType", scope.transactionType());
        }
    }

    public record PlatformRevenueSource(
            List<TransactionSource> transactions,
            List<AttemptSource> attempts,
            List<OrderSource> orders,
            List<EntitlementSource> entitlements) {

        public PlatformRevenueSource {
            transactions = List.copyOf(transactions == null ? List.of() : transactions);
            attempts = List.copyOf(attempts == null ? List.of() : attempts);
            orders = List.copyOf(orders == null ? List.of() : orders);
            entitlements = List.copyOf(entitlements == null ? List.of() : entitlements);
        }
    }

    public record TransactionSource(
            String publicId,
            LocalDateTime occurredAt,
            PlatformFinancialTransaction.TransactionType transactionType,
            PlatformFinancialTransaction.Direction direction,
            java.math.BigDecimal amount,
            String method,
            String provider,
            String orderPublicId,
            String orderCode,
            String planCode,
            String planName,
            String billingPeriod,
            SubscriptionOrder.Operation operation,
            com.hotel.paymentprovider.domain.FinancialStates.SubscriptionOrderState orderStatus,
            Long attemptId,
            String originalTransactionPublicId) {
    }

    public record AttemptSource(
            Long id,
            String publicId,
            LocalDateTime createdAt,
            PlatformPaymentAttempt.Status status,
            String provider,
            String method,
            java.math.BigDecimal expectedAmount,
            String orderPublicId,
            String planCode) {
    }

    public record OrderSource(
            Long id,
            String publicId,
            LocalDateTime createdAt,
            java.math.BigDecimal price,
            SubscriptionOrder.Operation operation,
            com.hotel.paymentprovider.domain.FinancialStates.SubscriptionOrderState status,
            String planCode,
            String planName,
            String billingPeriod,
            Long targetHotelId) {
    }

    public record EntitlementSource(
            Long id,
            LocalDateTime updatedAt,
            SubscriptionEntitlement.Status status,
            String planCode,
            String planName,
            LocalDateTime effectiveFrom,
            LocalDateTime effectiveUntil,
            boolean lifetime,
            Long targetHotelId) {
    }

    private record QueryScope(
            NormalizedFilters filters,
            LocalDateTime fromInclusive,
            LocalDateTime toExclusive,
            PlatformFinancialTransaction.TransactionType transactionType) {

        private static QueryScope from(NormalizedFilters filters) {
            Objects.requireNonNull(filters, "filters must not be null");
            if (filters.context() != FinancialContext.PLATFORM_BILLING) {
                throw new IllegalArgumentException("Platform revenue queries require Platform Billing filters.");
            }
            if (filters.propertyId() != null || filters.roomType() != null) {
                throw new IllegalArgumentException("Platform revenue queries cannot use property filters.");
            }
            PlatformFinancialTransaction.TransactionType transactionType = null;
            if (filters.transactionType() != null) {
                try {
                    transactionType = PlatformFinancialTransaction.TransactionType.valueOf(filters.transactionType());
                } catch (IllegalArgumentException exception) {
                    throw new IllegalArgumentException("Unsupported Platform Billing transaction type.", exception);
                }
            }
            return new QueryScope(
                    filters,
                    LocalDateTime.ofInstant(filters.fromInclusive(), ZoneOffset.UTC),
                    LocalDateTime.ofInstant(filters.toExclusive(), ZoneOffset.UTC),
                    transactionType);
        }

        private String provider() {
            return filters.provider();
        }

        private String method() {
            return filters.method();
        }

        private String planCode() {
            return filters.planCode();
        }
    }
}
