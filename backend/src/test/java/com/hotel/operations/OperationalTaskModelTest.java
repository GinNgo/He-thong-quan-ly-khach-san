package com.hotel.operations;

import com.hotel.entities.Hotel;
import com.hotel.entities.User;
import com.hotel.security.ActionCode;
import com.hotel.security.FunctionCode;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperationalTaskModelTest {

    @Test
    void supportsAssignmentClaimAndExactlyOnceCompletion() {
        Hotel hotel = new Hotel();
        hotel.setId(1L);
        User manager = user(10L);
        User receptionist = user(11L);
        LocalDateTime now = LocalDateTime.of(2026, 8, 9, 10, 0);
        OperationalTask task = OperationalTask.open(hotel, "CHECKIN", FunctionCode.CHECKIN,
                ActionCode.TASK_EXECUTE, "RESERVATION", "R-1", "CHECKIN:R-1");

        task.assign(receptionist, manager, now);
        task.claim(receptionist, now.plusMinutes(1));
        task.complete(receptionist, "CHECKIN:R-1:DONE", "Hoàn tất", now.plusMinutes(2));
        task.complete(receptionist, "CHECKIN:R-1:DONE", "Hoàn tất", now.plusMinutes(3));

        assertThat(task.getStatus()).isEqualTo(OperationalTask.Status.COMPLETED);
        assertThat(task.getAssignedTo().getId()).isEqualTo(11L);
        assertThat(task.getCompletedAt()).isEqualTo(now.plusMinutes(2));
    }

    @Test
    void rejectsCompletionByAnotherUser() {
        Hotel hotel = new Hotel();
        hotel.setId(1L);
        User manager = user(10L);
        User receptionist = user(11L);
        OperationalTask task = OperationalTask.open(hotel, "CHECKOUT", FunctionCode.CHECKOUT,
                ActionCode.TASK_EXECUTE, "RESERVATION", "R-2", "CHECKOUT:R-2");
        task.assign(receptionist, manager, LocalDateTime.now());

        assertThatThrownBy(() -> task.complete(user(12L), null, null, LocalDateTime.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("assigned user");
    }

    @Test
    void supportsBlockThenAuthorizedReassignmentAndProtectsTerminalState() {
        Hotel hotel = new Hotel();
        hotel.setId(1L);
        User manager = user(10L);
        OperationalTask task = OperationalTask.open(hotel, "RECONCILIATION", FunctionCode.RESERVATION_PAYMENT,
                ActionCode.TASK_EXECUTE, "PAYMENT_ATTEMPT", "P-1", "RECON:P-1");

        task.block("Quyền người xử lý đã bị thu hồi");
        task.assign(user(11L), manager, LocalDateTime.now());
        task.cancel("Không còn cần đối soát");

        assertThat(task.getStatus()).isEqualTo(OperationalTask.Status.CANCELLED);
        assertThatThrownBy(() -> task.assign(user(12L), manager, LocalDateTime.now()))
                .isInstanceOf(IllegalStateException.class);
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
