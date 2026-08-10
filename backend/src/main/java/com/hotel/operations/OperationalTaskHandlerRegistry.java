package com.hotel.operations;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class OperationalTaskHandlerRegistry {
    private final List<OperationalTaskHandler> handlers;

    public OperationalTaskHandlerRegistry(List<OperationalTaskHandler> handlers) {
        this.handlers = handlers;
    }

    public Result execute(OperationalTask task, String command, Object payload) {
        return handlers.stream()
                .filter(handler -> handler.supports(task.getTaskType()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No domain handler for task type " + task.getTaskType()))
                .execute(task, command == null ? "COMPLETE" : command.trim().toUpperCase(Locale.ROOT), payload);
    }

    public interface OperationalTaskHandler {
        boolean supports(String taskType);
        Result execute(OperationalTask task, String command, Object payload);
    }

    public record Result(String reference, String note) { }
}
