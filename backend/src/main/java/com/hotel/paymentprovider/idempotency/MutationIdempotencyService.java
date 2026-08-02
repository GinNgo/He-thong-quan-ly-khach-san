package com.hotel.paymentprovider.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * Executes a mutation behind the persisted idempotency ledger while keeping
 * the business transaction independent from the claim/response transactions.
 */
@Service
public class MutationIdempotencyService {

    private final FinancialIdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate requiresNew;

    @Autowired
    public MutationIdempotencyService(
            FinancialIdempotencyService idempotencyService,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager) {
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
    }

    MutationIdempotencyService(
            FinancialIdempotencyService idempotencyService,
            ObjectMapper objectMapper) {
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
        this.requiresNew = null;
    }

    public <T> T execute(
            FinancialIdempotencyService.BeginCommand command,
            int responseStatus,
            Class<T> responseType,
            Supplier<T> mutation) {
        return execute(command, responseStatus, responseType, mutation, () -> null);
    }

    /**
     * Executes a mutation and optionally recovers a business result that was
     * committed before the client lost the original response.
     */
    public <T> T execute(
            FinancialIdempotencyService.BeginCommand command,
            int responseStatus,
            Class<T> responseType,
            Supplier<T> mutation,
            Supplier<T> recovery) {
        FinancialIdempotencyService.BeginResult begin = begin(command);
        if (begin instanceof FinancialIdempotencyService.Replay replay) {
            return deserialize(replay.responseBody(), responseType);
        }
        if (begin instanceof FinancialIdempotencyService.InProgress) {
            T recovered = recovery.get();
            if (recovered != null) {
                complete(begin.recordId(), responseStatus, serialize(recovered));
                return recovered;
            }
            throw new FinancialException(
                    FinancialErrorCode.CONCURRENT_MODIFICATION,
                    "An equivalent request is still being processed. Retry with the same key after it completes.");
        }

        long recordId = begin.recordId();
        try {
            T response = mutation.get();
            complete(recordId, responseStatus, serialize(response));
            return response;
        } catch (RuntimeException exception) {
            fail(recordId);
            throw exception;
        }
    }

    private FinancialIdempotencyService.BeginResult begin(
            FinancialIdempotencyService.BeginCommand command) {
        if (requiresNew == null) return idempotencyService.begin(command);
        return requiresNew.execute(status -> idempotencyService.begin(command));
    }

    private void complete(long recordId, int responseStatus, String responseBody) {
        if (requiresNew == null) {
            idempotencyService.complete(recordId, responseStatus, responseBody);
            return;
        }
        requiresNew.executeWithoutResult(status -> idempotencyService.complete(recordId, responseStatus, responseBody));
    }

    private void fail(long recordId) {
        if (requiresNew == null) {
            idempotencyService.fail(recordId);
            return;
        }
        requiresNew.executeWithoutResult(status -> idempotencyService.fail(recordId));
    }

    private String serialize(Object response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to persist the idempotent response.", exception);
        }
    }

    private <T> T deserialize(String responseBody, Class<T> responseType) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new IllegalStateException("The stored idempotent response is unavailable.");
        }
        try {
            return objectMapper.readValue(responseBody, responseType);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to restore the idempotent response.", exception);
        }
    }
}
