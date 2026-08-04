package com.hotel.services;

import com.hotel.dtos.ChatConversationDTO;
import com.hotel.dtos.ChatMessageDTO;
import com.hotel.dtos.ChatPageDTO;
import com.hotel.entities.ChatMessage;
import com.hotel.entities.Hotel;
import com.hotel.entities.SupportConversation;
import com.hotel.entities.User;
import com.hotel.entities.UserProperty;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.repositories.ChatMessageRepository;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.repositories.SupportConversationRepository;
import com.hotel.repositories.UserPropertyRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.security.ActionCode;
import com.hotel.security.ChatAuthorizationService;
import com.hotel.security.CustomUserDetails;
import com.hotel.security.FunctionCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {
    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private ChatMessageIdempotencyWriter messageWriter;
    @Mock private SupportConversationRepository conversationRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserPropertyRepository userPropertyRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private HotelRepository hotelRepository;
    @Mock private SupportConversationAuditService auditService;

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(
                chatMessageRepository,
                messageWriter,
                conversationRepository,
                userRepository,
                userPropertyRepository,
                reservationRepository,
                hotelRepository,
                new ChatAuthorizationService(),
                auditService,
                90,
                30,
                10);
    }

    @Test
    void duplicateClientMessageReplaySkipsConversationAndAuditMutation() {
        CustomUserDetails customer = user(42L, Map.of(), "CUSTOMER");
        SupportConversation conversation = scopedConversation(9L, 42L, 5L);
        ChatMessage replay = savedMessage(new ChatMessage(), 99L, Instant.parse("2026-08-04T10:00:00Z"));
        replay.setConversationId(9L);
        replay.setHotelId(5L);
        replay.setSenderId(42L);
        replay.setReceiverId(0L);
        replay.setClientMessageId("client-1");
        replay.setContent("Xin chao");
        replay.setDeliveryStatus("PERSISTED");
        when(conversationRepository.findLockedById(9L)).thenReturn(Optional.of(conversation));
        when(messageWriter.createOrLoad(conversation, 42L, 0L, "client-1", "Xin chao"))
                .thenReturn(new ChatMessageIdempotencyWriter.WriteResult(replay, false));

        ChatMessageDTO result = chatService.sendToSupport(customer, 9L, "Xin chao", "client-1");

        assertEquals(99L, result.getId());
        assertEquals("client-1", result.getClientMessageId());
        verify(conversationRepository, never()).saveAndFlush(conversation);
        verify(auditService, never()).record(any(), any(), any(), any());
    }

    @Test
    void readAcknowledgementAddsImmutableConversationAuditEvent() {
        CustomUserDetails customer = user(42L, Map.of(), "CUSTOMER");
        SupportConversation conversation = scopedConversation(9L, 42L, 5L);
        ChatMessage message = message(77L, 9L, 7L, 42L, "Support reply", "2026-08-04T10:00:00Z");
        message.setDeliveryStatus("DELIVERED");
        when(chatMessageRepository.findLockedById(77L)).thenReturn(Optional.of(message));
        when(conversationRepository.findById(9L)).thenReturn(Optional.of(conversation));
        when(chatMessageRepository.saveAndFlush(message)).thenReturn(message);

        ChatMessageDTO result = chatService.acknowledgeMessage(customer, 77L, "READ");

        assertEquals("READ", result.getDeliveryStatus());
        verify(auditService).record(conversation, 42L, "MESSAGE_READ", "Message 77 changed from DELIVERED to READ");
    }

    @Test
    void firstCustomerMessageCreatesAConversationAndUsesAuthenticatedSender() {
        CustomUserDetails customer = user(42L, Map.of(), "CUSTOMER");
        when(userRepository.findById(42L)).thenReturn(Optional.of(customerEntity(42L)));
        when(reservationRepository.findByUserIdOrderByIdDesc(42L)).thenReturn(List.of());
        when(conversationRepository.findFirstByCustomerIdOrderByUpdatedAtDesc(42L)).thenReturn(Optional.empty());
        when(conversationRepository.saveAndFlush(any(SupportConversation.class))).thenAnswer(invocation -> {
            SupportConversation conversation = invocation.getArgument(0);
            if (conversation.getId() == null) conversation.setId(9L);
            if (conversation.getVersion() == null) conversation.setVersion(0L);
            return conversation;
        });
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> savedMessage(
                invocation.getArgument(0), 99L, Instant.parse("2026-08-04T10:00:00Z")));

        ChatMessageDTO result = chatService.sendToSupport(customer, "  Xin chao  ");

        assertEquals(9L, result.getConversationId());
        assertEquals(42L, result.getSenderId());
        assertEquals(0L, result.getReceiverId());
        assertEquals("Xin chao", result.getContent());
    }

    @Test
    void customerCannotReadOrSendToAnotherCustomersConversation() {
        CustomUserDetails customer = user(42L, Map.of(), "CUSTOMER");
        when(conversationRepository.findByIdAndCustomerId(91L, 42L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> chatService.getMyConversationMessages(customer, 91L, 0, 20));
        assertThrows(ResourceNotFoundException.class,
                () -> chatService.sendToSupport(customer, 91L, "cross account"));

        verify(chatMessageRepository, never()).save(any(ChatMessage.class));
    }

    @Test
    void customerCanSelectMultipleOwnConversations() {
        CustomUserDetails customer = user(42L, Map.of(), "CUSTOMER");
        SupportConversation newest = conversation(12L, 42L, "Hoa don", "2026-08-04T10:00:00Z");
        SupportConversation older = conversation(11L, 42L, "Dat phong", "2026-08-04T09:00:00Z");
        PageRequest request = PageRequest.of(0, 20);
        when(conversationRepository.findByCustomerIdAndUpdatedAtGreaterThanEqualOrderByUpdatedAtDescIdDesc(
                eq(42L), any(Instant.class), eq(request)))
                .thenReturn(new PageImpl<>(List.of(newest, older), request, 2));
        when(userRepository.findById(42L)).thenReturn(Optional.of(customerEntity(42L)));
        when(chatMessageRepository.findFirstByConversationIdOrderByTimestampDesc(any()))
                .thenReturn(Optional.empty());

        ChatPageDTO<ChatConversationDTO> page = chatService.getMyConversations(customer, 0, 20);

        assertEquals(List.of(12L, 11L), page.content().stream()
                .map(ChatConversationDTO::getConversationId).toList());
        assertEquals(90, page.retentionDays());
    }

    @Test
    void messagePagesAreChronologicalAndExposeRetention() {
        CustomUserDetails customer = user(42L, Map.of(), "CUSTOMER");
        SupportConversation conversation = conversation(9L, 42L, "Dat phong", "2026-08-04T10:00:00Z");
        ChatMessage newer = message(2L, 9L, 7L, 42L, "reply", "2026-08-04T10:02:00Z");
        ChatMessage older = message(1L, 9L, 42L, 0L, "question", "2026-08-04T10:01:00Z");
        PageRequest request = PageRequest.of(1, 2);
        when(conversationRepository.findByIdAndCustomerId(9L, 42L)).thenReturn(Optional.of(conversation));
        when(chatMessageRepository.findByConversationIdAndTimestampGreaterThanEqualOrderByTimestampDescIdDesc(
                eq(9L), any(Instant.class), eq(request)))
                .thenReturn(new PageImpl<>(List.of(newer, older), request, 4));
        Instant earliestExpectedCutoff = Instant.now().minus(90, ChronoUnit.DAYS).minusSeconds(2);

        ChatPageDTO<ChatMessageDTO> page = chatService.getMyConversationMessages(customer, 9L, 1, 2);

        assertEquals(List.of("question", "reply"), page.content().stream()
                .map(ChatMessageDTO::getContent).toList());
        assertEquals(4, page.totalElements());
        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(chatMessageRepository).findByConversationIdAndTimestampGreaterThanEqualOrderByTimestampDescIdDesc(
                eq(9L), cutoff.capture(), eq(request));
        assertTrue(cutoff.getValue().isAfter(earliestExpectedCutoff));
    }

    @Test
    void supportReplyRequiresCreatePermission() {
        CustomUserDetails nonSupport = user(7L, Map.of(), "SUPPORT");

        assertThrows(AccessDeniedException.class,
                () -> chatService.replyToConversation(nonSupport, 9L, "Phan hoi"));

        verify(conversationRepository, never()).findById(any());
    }

    @Test
    void supportReplyAutoClaimsTenantConversationAndClearsPendingSla() {
        CustomUserDetails support = user(7L, Map.of(FunctionCode.AI_CHAT, ActionCode.CREATE), "SUPPORT");
        SupportConversation conversation = scopedConversation(9L, 42L, 5L);
        when(conversationRepository.findById(9L)).thenReturn(Optional.of(conversation));
        when(userPropertyRepository.findByUserId(7L)).thenReturn(List.of(assignment(7L, 5L)));
        when(userRepository.findById(7L)).thenReturn(Optional.of(customerEntity(7L)));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> savedMessage(
                invocation.getArgument(0), 100L, Instant.parse("2026-08-04T10:05:00Z")));
        when(conversationRepository.saveAndFlush(conversation)).thenReturn(conversation);

        ChatMessageDTO reply = chatService.replyToConversation(support, 9L, " Da tiep nhan ");

        assertEquals(9L, reply.getConversationId());
        assertEquals(7L, conversation.getAssignedAgentId());
        assertEquals("ASSIGNED", conversation.getStatus());
        assertEquals(null, conversation.getSlaDeadlineAt());
    }

    @Test
    void supportQueueFiltersByTenantAssignmentAndBreachedSla() {
        CustomUserDetails support = user(7L, Map.of(FunctionCode.AI_CHAT, ActionCode.VIEW), "SUPPORT");
        SupportConversation breached = scopedConversation(9L, 42L, 5L);
        breached.setSlaDeadlineAt(Instant.now().minusSeconds(60));
        SupportConversation otherTenant = scopedConversation(10L, 43L, 6L);
        when(userPropertyRepository.findByUserId(7L)).thenReturn(List.of(assignment(7L, 5L)));
        when(conversationRepository.findByHotelIdInOrderByLastActivityAtDesc(Set.of(5L)))
                .thenReturn(List.of(breached));
        when(userRepository.findById(7L)).thenReturn(Optional.of(customerEntity(7L)));
        when(userRepository.findById(42L)).thenReturn(Optional.of(customerEntity(42L)));
        when(hotelRepository.findById(5L)).thenReturn(Optional.of(hotel(5L)));
        when(chatMessageRepository.findFirstByConversationIdOrderByTimestampDesc(9L)).thenReturn(Optional.empty());

        List<ChatConversationDTO> queue = chatService.getSupportConversations(
                support, "OPEN", "UNASSIGNED", "BREACHED", 5L);

        assertEquals(1, queue.size());
        assertEquals(9L, queue.getFirst().getConversationId());
        assertEquals("BREACHED", queue.getFirst().getSlaState());
        verify(conversationRepository, never()).findById(otherTenant.getId());
    }

    @Test
    void staleQueueMutationReturnsOptimisticConflictBeforeAssignment() {
        CustomUserDetails support = user(7L, Map.of(FunctionCode.AI_CHAT, ActionCode.CREATE), "SUPPORT");
        SupportConversation conversation = scopedConversation(9L, 42L, 5L);
        conversation.setVersion(4L);
        when(conversationRepository.findById(9L)).thenReturn(Optional.of(conversation));
        when(userPropertyRepository.findByUserId(7L)).thenReturn(List.of(assignment(7L, 5L)));

        assertThrows(OptimisticLockingFailureException.class,
                () -> chatService.claimConversation(support, 9L, 3L));

        verify(conversationRepository, never()).saveAndFlush(conversation);
    }

    @Test
    void supportQueueSearchesWithinTheAlreadyScopedTenantRows() {
        CustomUserDetails support = user(7L, Map.of(FunctionCode.AI_CHAT, ActionCode.VIEW), "SUPPORT");
        SupportConversation invoice = scopedConversation(9L, 42L, 5L);
        invoice.setSubject("Hoa don thang tam");
        SupportConversation booking = scopedConversation(10L, 43L, 5L);
        booking.setSubject("Dat phong");
        when(userPropertyRepository.findByUserId(7L)).thenReturn(List.of(assignment(7L, 5L)));
        when(conversationRepository.findByHotelIdInOrderByLastActivityAtDesc(Set.of(5L)))
                .thenReturn(List.of(invoice, booking));
        when(userRepository.findById(7L)).thenReturn(Optional.of(customerEntity(7L)));
        when(userRepository.findById(42L)).thenReturn(Optional.of(customerEntity(42L)));
        when(userRepository.findById(43L)).thenReturn(Optional.of(customerEntity(43L)));
        when(hotelRepository.findById(5L)).thenReturn(Optional.of(hotel(5L)));
        when(chatMessageRepository.findFirstByConversationIdOrderByTimestampDesc(any()))
                .thenReturn(Optional.empty());

        List<ChatConversationDTO> queue = chatService.getSupportConversations(
                support, "OPEN", "ALL", "ALL", 5L, "hoa DON");

        assertEquals(List.of(9L), queue.stream().map(ChatConversationDTO::getConversationId).toList());
    }

    @Test
    void reasonedCloseAndReopenPersistTimestampsAndAuditEvents() {
        CustomUserDetails support = user(7L, Map.of(FunctionCode.AI_CHAT, ActionCode.CREATE), "SUPPORT");
        SupportConversation conversation = scopedConversation(9L, 42L, 5L);
        conversation.setAssignedAgentId(7L);
        conversation.setVersion(4L);
        when(conversationRepository.findById(9L)).thenReturn(Optional.of(conversation));
        when(userPropertyRepository.findByUserId(7L)).thenReturn(List.of(assignment(7L, 5L)));
        when(conversationRepository.saveAndFlush(conversation)).thenReturn(conversation);
        when(userRepository.findById(42L)).thenReturn(Optional.of(customerEntity(42L)));
        when(userRepository.findById(7L)).thenReturn(Optional.of(customerEntity(7L)));
        when(hotelRepository.findById(5L)).thenReturn(Optional.of(hotel(5L)));
        when(chatMessageRepository.findFirstByConversationIdOrderByTimestampDesc(9L))
                .thenReturn(Optional.empty());

        ChatConversationDTO closed = chatService.closeConversation(support, 9L, 4L, " Da xu ly xong ");
        assertEquals("CLOSED", closed.getStatus());
        assertEquals("Da xu ly xong", closed.getClosedReason());
        assertEquals(null, closed.getSlaDeadlineAt());
        verify(auditService).record(conversation, 7L, "CLOSED", "Da xu ly xong");

        conversation.setVersion(5L);
        ChatConversationDTO reopened = chatService.reopenConversation(
                support, 9L, 5L, "Khach phan hoi them");
        assertEquals("OPEN", reopened.getStatus());
        assertEquals("Khach phan hoi them", reopened.getReopenReason());
        assertTrue(reopened.getReopenedAt() != null);
        verify(auditService).record(conversation, 7L, "REOPENED", "Khach phan hoi them");
    }

    private SupportConversation conversation(Long id, Long customerId, String subject, String updatedAt) {
        SupportConversation conversation = new SupportConversation();
        conversation.setId(id);
        conversation.setCustomerId(customerId);
        conversation.setSubject(subject);
        conversation.setStatus("OPEN");
        conversation.setChannel("IN_APP");
        conversation.setVersion(0L);
        conversation.setLastActivityAt(Instant.parse(updatedAt));
        conversation.setCreatedAt(Instant.parse(updatedAt));
        conversation.setUpdatedAt(Instant.parse(updatedAt));
        return conversation;
    }

    private SupportConversation scopedConversation(Long id, Long customerId, Long hotelId) {
        SupportConversation conversation = conversation(
                id, customerId, "Support", Instant.now().minusSeconds(30).toString());
        conversation.setHotelId(hotelId);
        conversation.setSlaDeadlineAt(Instant.now().plusSeconds(600));
        return conversation;
    }

    private ChatMessage message(
            Long id, Long conversationId, Long senderId, Long receiverId, String content, String timestamp) {
        ChatMessage message = new ChatMessage();
        message.setId(id);
        message.setConversationId(conversationId);
        message.setSenderId(senderId);
        message.setReceiverId(receiverId);
        message.setContent(content);
        message.setTimestamp(Instant.parse(timestamp));
        return message;
    }

    private ChatMessage savedMessage(ChatMessage message, Long id, Instant timestamp) {
        message.setId(id);
        message.setTimestamp(timestamp);
        return message;
    }

    private User customerEntity(Long id) {
        User customer = new User();
        customer.setId(id);
        customer.setUsername("user" + id);
        customer.setFullName("User " + id);
        return customer;
    }

    private UserProperty assignment(Long userId, Long hotelId) {
        UserProperty assignment = new UserProperty();
        assignment.setUser(customerEntity(userId));
        assignment.setHotel(hotel(hotelId));
        assignment.setStatus("ACTIVE");
        return assignment;
    }

    private Hotel hotel(Long id) {
        Hotel hotel = new Hotel();
        hotel.setId(id);
        hotel.setName("Hotel " + id);
        hotel.setApprovalStatus("APPROVED");
        hotel.setOperationStatus("ACTIVE");
        return hotel;
    }

    private CustomUserDetails user(Long id, Map<FunctionCode, Integer> masks, String authority) {
        return new CustomUserDetails(
                "user" + id,
                "hash",
                Set.of(new SimpleGrantedAuthority(authority)),
                masks,
                id,
                null,
                Map.of());
    }
}
