package com.hotel.operations;

import com.hotel.dtos.HousekeepingCommandRequest;
import com.hotel.housekeeping.HousekeepingQueueService;
import org.springframework.stereotype.Component;

@Component
public class HousekeepingOperationalTaskHandler implements OperationalTaskHandlerRegistry.OperationalTaskHandler {
    private final HousekeepingQueueService housekeeping;

    public HousekeepingOperationalTaskHandler(HousekeepingQueueService housekeeping) {
        this.housekeeping = housekeeping;
    }

    @Override
    public boolean supports(String taskType) {
        return "HOUSEKEEPING".equalsIgnoreCase(taskType);
    }

    @Override
    public OperationalTaskHandlerRegistry.Result execute(OperationalTask task, String command, Object payload) {
        long domainId;
        try {
            domainId = Long.parseLong(task.getAggregateId());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Housekeeping task aggregate ID is invalid.");
        }
        Long expectedVersion = payload instanceof Number number ? number.longValue() : null;
        housekeeping.complete(domainId, new HousekeepingCommandRequest(expectedVersion));
        return new OperationalTaskHandlerRegistry.Result("HOUSEKEEPING:" + domainId + ":COMPLETED", "Đã hoàn tất dọn phòng");
    }
}

