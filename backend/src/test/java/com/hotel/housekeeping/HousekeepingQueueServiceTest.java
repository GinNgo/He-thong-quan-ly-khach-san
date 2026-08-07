package com.hotel.housekeeping;

import com.hotel.dtos.HousekeepingAssignRequest;
import com.hotel.dtos.HousekeepingCommandRequest;
import com.hotel.entities.Hotel;
import com.hotel.entities.HousekeepingTask;
import com.hotel.entities.Role;
import com.hotel.entities.Room;
import com.hotel.entities.User;
import com.hotel.repositories.HousekeepingTaskRepository;
import com.hotel.repositories.RoomRepository;
import com.hotel.repositories.UserPropertyRepository;
import com.hotel.services.PropertyAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HousekeepingQueueServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-04T04:00:00Z");

    @Mock HousekeepingTaskRepository taskRepository;
    @Mock RoomRepository roomRepository;
    @Mock UserPropertyRepository userPropertyRepository;
    @Mock PropertyAccessService propertyAccessService;

    private HousekeepingQueueService service;
    private Hotel hotel;
    private Room room;
    private User cleaner;
    private User secondCleaner;

    @BeforeEach
    void setUp() {
        service = new HousekeepingQueueService(taskRepository, roomRepository, userPropertyRepository,
                propertyAccessService, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(30));
        hotel = new Hotel();
        hotel.setId(10L);
        room = new Room();
        room.setId(100L);
        room.setHotel(hotel);
        room.setRoomNumber("101");
        room.setStatus("DIRTY");
        room.setHousekeepingStatus("DIRTY");
        room.setMaintenanceStatus("NONE");
        cleaner = user(7L, "cleaner", "HOUSEKEEPING");
        secondCleaner = user(8L, "other-cleaner", "HOUSEKEEPING");
        lenient().when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        lenient().when(propertyAccessService.currentUser()).thenReturn(cleaner);
        lenient().when(userPropertyRepository.findByUserIdAndHotelIdAndRelationshipType(
                cleaner.getId(), hotel.getId(), "HOUSEKEEPING"))
                .thenReturn(Optional.of(mappingStatus("ACTIVE")));
        lenient().when(userPropertyRepository.findActiveHousekeepingUsers(hotel.getId()))
                .thenReturn(List.of(cleaner, secondCleaner));
        lenient().when(taskRepository.saveAndFlush(any(HousekeepingTask.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(roomRepository.saveAndFlush(any(Room.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void claimOwnPendingTaskIsIdempotentAndTenantScoped() {
        HousekeepingTask task = task(1L, "PENDING", null, null);
        when(taskRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(task));

        var claimed = service.claim(1L, new HousekeepingCommandRequest(0L));
        var replay = service.claim(1L, new HousekeepingCommandRequest(claimed.version()));

        assertThat(claimed.status()).isEqualTo("CLAIMED");
        assertThat(claimed.assignedToUserId()).isEqualTo(7L);
        assertThat(replay.status()).isEqualTo("CLAIMED");
        verify(propertyAccessService, times(4)).requireAccessibleOrNotFound(10L, "housekeeping task");
    }

    @Test
    void staleClaimCanBeTakenOverButFreshClaimConflicts() {
        HousekeepingTask fresh = task(2L, "CLAIMED", secondCleaner, NOW.minusSeconds(60));
        when(taskRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(fresh));
        assertThatThrownBy(() -> service.claim(2L, new HousekeepingCommandRequest(0L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("currently assigned");

        HousekeepingTask stale = task(3L, "CLAIMED", secondCleaner, NOW.minusSeconds(3601));
        when(taskRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(stale));
        var takeover = service.claim(3L, new HousekeepingCommandRequest(0L));
        assertThat(takeover.assignedToUserId()).isEqualTo(7L);
        assertThat(takeover.staleAssignment()).isFalse();
    }

    @Test
    void assignmentRejectsUnknownOrCrossPropertyCleaner() {
        HousekeepingTask task = task(4L, "PENDING", null, null);
        when(taskRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(task));
        when(userPropertyRepository.findActiveHousekeepingUsers(10L)).thenReturn(List.of(cleaner));
        when(propertyAccessService.currentUser()).thenReturn(user(9L, "manager", "HOTEL_MANAGER"));

        assertThatThrownBy(() -> service.assign(4L, new HousekeepingAssignRequest(999L, 0L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void crossPropertyTaskIsHiddenFromTheCurrentTenant() {
        Hotel foreignHotel = new Hotel();
        foreignHotel.setId(99L);
        HousekeepingTask task = task(7L, "PENDING", null, null);
        task.setHotel(foreignHotel);
        when(taskRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(task));
        org.mockito.Mockito.doThrow(new com.hotel.exceptions.ResourceNotFoundException("not found"))
                .when(propertyAccessService).requireAccessibleOrNotFound(99L, "housekeeping task");

        assertThatThrownBy(() -> service.claim(7L, new HousekeepingCommandRequest(0L)))
                .isInstanceOf(com.hotel.exceptions.ResourceNotFoundException.class);
    }

    @Test
    void startMovesDirtyRoomToCleaningForAssignedCleaner() {
        HousekeepingTask task = task(5L, "CLAIMED", cleaner, NOW.minusSeconds(60));
        when(taskRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(task));
        when(roomRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(room));

        var started = service.start(5L, new HousekeepingCommandRequest(0L));

        assertThat(started.status()).isEqualTo("IN_PROGRESS");
        assertThat(started.startedAt()).isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        assertThat(room.getStatus()).isEqualTo("CLEANING");
        assertThat(room.getHousekeepingStatus()).isEqualTo("CLEANING");
        verify(roomRepository).saveAndFlush(room);
    }

    @Test
    void staleVersionFailsClosedBeforeMutation() {
        HousekeepingTask task = task(6L, "PENDING", null, null);
        task.setVersion(3L);
        when(taskRepository.findByIdForUpdate(6L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.claim(6L, new HousekeepingCommandRequest(2L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("changed");
    }

    @Test
    void assignedCleanerCompletesTaskAndReleasesCleanRoom() {
        room.setStatus("CLEANING");
        room.setHousekeepingStatus("CLEANING");
        HousekeepingTask task = task(8L, "IN_PROGRESS", cleaner, NOW.minusSeconds(600));
        task.setStartedAt(LocalDateTime.ofInstant(NOW.minusSeconds(300), ZoneOffset.UTC));
        when(taskRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(task));
        when(roomRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(room));

        var completed = service.complete(8L, new HousekeepingCommandRequest(0L));

        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(completed.completedAt()).isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        assertThat(completed.roomStatus()).isEqualTo("AVAILABLE");
        assertThat(completed.roomHousekeepingStatus()).isEqualTo("CLEAN");
        assertThat(completed.roomReleased()).isTrue();
        verify(roomRepository).saveAndFlush(room);
        verify(taskRepository).saveAndFlush(task);
    }

    @Test
    void wrongAssigneeCannotCompleteTask() {
        HousekeepingTask task = task(9L, "IN_PROGRESS", secondCleaner, NOW.minusSeconds(600));
        when(taskRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.complete(9L, new HousekeepingCommandRequest(0L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("assigned housekeeper");
        verify(roomRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void crossPropertyCompletionIsHiddenBeforeMutation() {
        Hotel foreignHotel = new Hotel();
        foreignHotel.setId(99L);
        HousekeepingTask task = task(10L, "IN_PROGRESS", cleaner, NOW.minusSeconds(600));
        task.setHotel(foreignHotel);
        when(taskRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(task));
        org.mockito.Mockito.doThrow(new com.hotel.exceptions.ResourceNotFoundException("not found"))
                .when(propertyAccessService).requireAccessibleOrNotFound(99L, "housekeeping task");

        assertThatThrownBy(() -> service.complete(10L, new HousekeepingCommandRequest(0L)))
                .isInstanceOf(com.hotel.exceptions.ResourceNotFoundException.class);
        verify(roomRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void completionRejectsStaleVersionBeforeRoomMutation() {
        HousekeepingTask task = task(11L, "IN_PROGRESS", cleaner, NOW.minusSeconds(600));
        task.setVersion(4L);
        when(taskRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.complete(11L, new HousekeepingCommandRequest(3L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("changed");
        verify(roomRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void completionReplayReturnsStoredTerminalStateWithoutAnotherWrite() {
        room.setStatus("CLEANING");
        room.setHousekeepingStatus("CLEANING");
        HousekeepingTask task = task(12L, "IN_PROGRESS", cleaner, NOW.minusSeconds(600));
        when(taskRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(task));
        when(roomRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(room));

        var completed = service.complete(12L, new HousekeepingCommandRequest(0L));
        var replay = service.complete(12L, new HousekeepingCommandRequest(0L));

        assertThat(replay).isEqualTo(completed);
        verify(roomRepository, times(1)).findByIdForUpdate(100L);
        verify(roomRepository, times(1)).saveAndFlush(room);
        verify(taskRepository, times(1)).saveAndFlush(task);
    }

    @Test
    void completionCleansRoomButPreservesMaintenanceBlock() {
        room.setStatus("MAINTENANCE");
        room.setHousekeepingStatus("CLEANING");
        room.setMaintenanceStatus("MAINTENANCE");
        HousekeepingTask task = task(13L, "IN_PROGRESS", cleaner, NOW.minusSeconds(600));
        when(taskRepository.findByIdForUpdate(13L)).thenReturn(Optional.of(task));
        when(roomRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(room));

        var completed = service.complete(13L, new HousekeepingCommandRequest(0L));

        assertThat(completed.roomHousekeepingStatus()).isEqualTo("CLEAN");
        assertThat(completed.roomStatus()).isEqualTo("MAINTENANCE");
        assertThat(completed.roomMaintenanceStatus()).isEqualTo("MAINTENANCE");
        assertThat(completed.roomReleased()).isFalse();
    }

    private HousekeepingTask task(Long id, String status, User assignedTo, Instant assignedAt) {
        HousekeepingTask task = new HousekeepingTask();
        task.setId(id);
        task.setHotel(hotel);
        task.setRoom(room);
        task.setStatus(status);
        task.setAssignedTo(assignedTo);
        task.setAssignedAt(assignedAt == null ? null : LocalDateTime.ofInstant(assignedAt, ZoneOffset.UTC));
        task.setVersion(0L);
        return task;
    }

    private User user(Long id, String username, String roleCode) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setFullName(username);
        Role role = new Role();
        role.setCode(roleCode);
        user.setRoles(Set.of(role));
        return user;
    }

    private com.hotel.entities.UserProperty mappingStatus(String status) {
        var mapping = new com.hotel.entities.UserProperty();
        mapping.setStatus(status);
        return mapping;
    }
}
