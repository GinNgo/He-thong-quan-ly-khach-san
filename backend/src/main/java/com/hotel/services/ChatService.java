package com.hotel.services;

import com.hotel.dtos.ChatConversationDTO;
import com.hotel.dtos.ChatMessageDTO;
import com.hotel.dtos.ChatPageDTO;
import com.hotel.entities.ChatMessage;
import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class ChatService {
    private static final Set<String> ACTIVE_STATUSES = Set.of("OPEN", "ASSIGNED", "ESCALATED");
    private static final Set<String> QUEUE_STATUSES = Set.of("OPEN", "ASSIGNED", "ESCALATED", "CLOSED");
    private static final Set<String> ASSIGNMENT_FILTERS = Set.of("ALL", "UNASSIGNED", "MINE");
    private static final Set<String> SLA_FILTERS = Set.of(
            "ALL", "BREACHED", "AT_RISK", "ON_TRACK", "NO_PENDING_RESPONSE");

    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageIdempotencyWriter messageWriter;
    private final SupportConversationRepository conversationRepository;
    private final UserRepository userRepository;
    private final UserPropertyRepository userPropertyRepository;
    private final ReservationRepository reservationRepository;
    private final HotelRepository hotelRepository;
    private final ChatAuthorizationService authorizationService;
    private final SupportConversationAuditService auditService;
    private final int retentionDays;
    private final int slaResponseMinutes;
    private final int slaAtRiskMinutes;

    public ChatService(
            ChatMessageRepository chatMessageRepository,
            ChatMessageIdempotencyWriter messageWriter,
            SupportConversationRepository conversationRepository,
            UserRepository userRepository,
            UserPropertyRepository userPropertyRepository,
            ReservationRepository reservationRepository,
            HotelRepository hotelRepository,
            ChatAuthorizationService authorizationService,
            SupportConversationAuditService auditService,
            @Value("${app.chat.retention-days:365}") int retentionDays,
            @Value("${app.chat.sla-response-minutes:30}") int slaResponseMinutes,
            @Value("${app.chat.sla-at-risk-minutes:10}") int slaAtRiskMinutes) {
        this.chatMessageRepository = chatMessageRepository;
        this.messageWriter = messageWriter;
        this.conversationRepository = conversationRepository;
        this.userRepository = userRepository;
        this.userPropertyRepository = userPropertyRepository;
        this.reservationRepository = reservationRepository;
        this.hotelRepository = hotelRepository;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
        this.retentionDays = Math.min(Math.max(retentionDays, 30), 3650);
        this.slaResponseMinutes = Math.min(Math.max(slaResponseMinutes, 5), 1440);
        this.slaAtRiskMinutes = Math.min(Math.max(slaAtRiskMinutes, 1), this.slaResponseMinutes);
    }

    @Transactional
    public ChatConversationDTO createConversation(CustomUserDetails customer, String subject) {
        return createConversation(customer, subject, null, null);
    }

    @Transactional
    public ChatConversationDTO createConversation(
            CustomUserDetails customer, String subject, Long requestedHotelId, Long reservationId) {
        CustomerContext context = resolveCustomerContext(customer.getUserId(), requestedHotelId, reservationId);
        SupportConversation conversation = createEntity(customer.getUserId(), normalizeSubject(subject), context);
        auditService.record(conversation, customer.getUserId(), "CREATED", "Customer opened support conversation");
        return toConversation(conversation);
    }

    @Transactional
    public ChatMessageDTO sendToSupport(CustomUserDetails sender, Long conversationId, String content) {
        return sendToSupport(sender, conversationId, content, null);
    }

    @Transactional
    public ChatMessageDTO sendToSupport(
            CustomUserDetails sender, Long conversationId, String content, String clientMessageId) {
        String normalizedClientMessageId = normalizeClientMessageId(clientMessageId);
        SupportConversation conversation = conversationId == null
                ? conversationRepository.findFirstByCustomerIdOrderByUpdatedAtDesc(sender.getUserId())
                        .filter(item -> !"CLOSED".equals(item.getStatus()))
                        .orElseGet(() -> createEntity(sender.getUserId(), "Ho tro chung", resolveCustomerContext(
                                sender.getUserId(), null, null)))
                : normalizedClientMessageId == null
                        ? requireOwnedConversation(conversationId, sender.getUserId())
                        : requireLockedConversation(conversationId);
        if (conversationId == null) {
            conversation = lockConversationForIdempotentSend(conversation, normalizedClientMessageId);
        }
        if (!sender.getUserId().equals(conversation.getCustomerId())) {
            throw new ResourceNotFoundException("Support conversation not found.");
        }
        return persistCustomerMessage(sender, conversation, content, normalizedClientMessageId);
    }

    @Transactional
    public ChatMessageDTO sendToSupport(
            CustomUserDetails sender, Long requestedHotelId, Long reservationId, String content) {
        return sendToSupport(sender, requestedHotelId, reservationId, content, null);
    }

    @Transactional
    public ChatMessageDTO sendToSupport(
            CustomUserDetails sender,
            Long requestedHotelId,
            Long reservationId,
            String content,
            String clientMessageId) {
        String normalizedClientMessageId = normalizeClientMessageId(clientMessageId);
        CustomerContext context = resolveCustomerContext(sender.getUserId(), requestedHotelId, reservationId);
        SupportConversation conversation;
        if (context.hotel() == null) {
            conversation = conversationRepository.findFirstByCustomerIdOrderByUpdatedAtDesc(sender.getUserId())
                    .filter(item -> item.getHotelId() == null && !"CLOSED".equals(item.getStatus()))
                    .orElseGet(() -> createEntity(sender.getUserId(), "Ho tro chung", context));
        } else {
            conversation = conversationRepository
                    .findFirstByCustomerIdAndHotelIdAndChannelAndStatusInOrderByLastActivityAtDesc(
                            sender.getUserId(), context.hotel().getId(), "IN_APP", ACTIVE_STATUSES)
                    .orElseGet(() -> createEntity(sender.getUserId(), "Ho tro chung", context));
        }
        if (conversation.getReservationId() == null && context.reservation() != null) {
            conversation.setReservation(context.reservation());
        }
        conversation = lockConversationForIdempotentSend(conversation, normalizedClientMessageId);
        if (!sender.getUserId().equals(conversation.getCustomerId())) {
            throw new ResourceNotFoundException("Support conversation not found.");
        }
        return persistCustomerMessage(sender, conversation, content, normalizedClientMessageId);
    }

    @Transactional
    public ChatMessageDTO sendToSupport(CustomUserDetails sender, String content) {
        return sendToSupport(sender, null, content);
    }

    @Transactional
    public ChatMessageDTO replyToConversation(
            CustomUserDetails support, Long conversationId, String content) {
        return replyToConversation(support, conversationId, content, null);
    }

    @Transactional
    public ChatMessageDTO replyToConversation(
            CustomUserDetails support, Long conversationId, String content, Long expectedVersion) {
        return replyToConversation(support, conversationId, content, expectedVersion, null);
    }

    @Transactional
    public ChatMessageDTO replyToConversation(
            CustomUserDetails support,
            Long conversationId,
            String content,
            Long expectedVersion,
            String clientMessageId) {
        authorizationService.requirePermission(support, ActionCode.CREATE);
        String normalizedClientMessageId = normalizeClientMessageId(clientMessageId);
        SupportConversation conversation;
        if (normalizedClientMessageId == null) {
            conversation = requireAccessibleConversation(support, conversationId, "REPLY");
        } else {
            conversation = requireLockedConversation(conversationId);
            assertAccessibleConversation(support, conversation, "REPLY");
        }
        String normalizedContent = normalizeContent(content);
        MessageWrite write = writeMessage(
                conversation,
                support.getUserId(),
                conversation.getCustomerId(),
                normalizedContent,
                normalizedClientMessageId);
        if (!write.created()) return write.message();
        assertExpectedVersion(conversation, expectedVersion);
        assignForReply(conversation, support);
        Instant now = Instant.now();
        conversation.setLastActivityAt(now);
        conversation.setLastSupportReplyAt(now);
        if (conversation.getFirstResponseAt() == null) conversation.setFirstResponseAt(now);
        conversation.setSlaDeadlineAt(null);
        conversationRepository.saveAndFlush(conversation);
        auditService.record(conversation, support.getUserId(), "REPLIED", "Support reply accepted");
        return write.message();
    }

    @Transactional
    public ChatMessageDTO replyToCustomer(CustomUserDetails support, Long conversationId, String content) {
        return replyToConversation(support, conversationId, content, null);
    }

    @Transactional
    public ChatConversationDTO claimConversation(CustomUserDetails support, Long conversationId) {
        return claimConversation(support, conversationId, null);
    }

    @Transactional
    public ChatConversationDTO claimConversation(
            CustomUserDetails support, Long conversationId, Long expectedVersion) {
        authorizationService.requirePermission(support, ActionCode.CREATE);
        SupportConversation conversation = requireAccessibleConversation(support, conversationId, "ASSIGN");
        assertExpectedVersion(conversation, expectedVersion);
        if ("CLOSED".equals(conversation.getStatus())) {
            throw new IllegalStateException("Closed conversations must be reopened before assignment.");
        }
        User agent = requireUser(support.getUserId());
        if (conversation.getAssignedAgentId() != null
                && !conversation.getAssignedAgentId().equals(agent.getId())
                && !authorizationService.isSystemAdministrator(support)) {
            auditService.recordDenied(
                    conversation, support.getUserId(), "ACCESS_DENIED_ASSIGN", "Conversation already assigned");
            throw new AccessDeniedException("Conversation is assigned to another support agent");
        }
        if (agent.getId().equals(conversation.getAssignedAgentId())
                && "ASSIGNED".equals(conversation.getStatus())) {
            return toConversation(conversation);
        }
        conversation.setAssignedAgentId(agent.getId());
        conversation.setAssignedAt(Instant.now());
        conversation.setStatus("ASSIGNED");
        conversationRepository.saveAndFlush(conversation);
        auditService.record(conversation, support.getUserId(), "ASSIGNED", "Conversation assigned to support agent");
        return toConversation(conversation);
    }

    @Transactional
    public ChatConversationDTO unassignConversation(
            CustomUserDetails support, Long conversationId, Long expectedVersion) {
        authorizationService.requirePermission(support, ActionCode.CREATE);
        SupportConversation conversation = requireAccessibleConversation(support, conversationId, "UNASSIGN");
        assertExpectedVersion(conversation, expectedVersion);
        if (conversation.getAssignedAgentId() != null
                && !conversation.getAssignedAgentId().equals(support.getUserId())
                && !authorizationService.isSystemAdministrator(support)) {
            auditService.recordDenied(
                    conversation, support.getUserId(), "ACCESS_DENIED_UNASSIGN", "Conversation assigned to another agent");
            throw new AccessDeniedException("Only the assigned agent can unassign this conversation");
        }
        conversation.setAssignedAgentId(null);
        conversation.setAssignedAt(null);
        conversation.setStatus("OPEN");
        conversationRepository.saveAndFlush(conversation);
        auditService.record(conversation, support.getUserId(), "UNASSIGNED", "Conversation returned to tenant queue");
        return toConversation(conversation);
    }

    @Transactional
    public ChatConversationDTO escalateConversation(CustomUserDetails support, Long conversationId) {
        return escalateConversation(support, conversationId, null);
    }

    @Transactional
    public ChatConversationDTO escalateConversation(
            CustomUserDetails support, Long conversationId, Long expectedVersion) {
        authorizationService.requirePermission(support, ActionCode.CREATE);
        SupportConversation conversation = requireAccessibleConversation(support, conversationId, "ESCALATE");
        assertExpectedVersion(conversation, expectedVersion);
        if ("CLOSED".equals(conversation.getStatus())) {
            throw new IllegalStateException("Closed conversations must be reopened before escalation.");
        }
        if (conversation.getAssignedAgentId() != null
                && !conversation.getAssignedAgentId().equals(support.getUserId())
                && !authorizationService.isSystemAdministrator(support)) {
            auditService.recordDenied(
                    conversation, support.getUserId(), "ACCESS_DENIED_ESCALATE", "Conversation assigned to another agent");
            throw new AccessDeniedException("Only the assigned agent can escalate this conversation");
        }
        conversation.setAssignedAgentId(null);
        conversation.setAssignedAt(null);
        conversation.setStatus("ESCALATED");
        conversation.setEscalatedAt(Instant.now());
        conversationRepository.saveAndFlush(conversation);
        auditService.record(conversation, support.getUserId(), "ESCALATED", "Conversation returned to tenant queue");
        return toConversation(conversation);
    }

    @Transactional
    public ChatConversationDTO reopenConversation(
            CustomUserDetails support, Long conversationId, Long expectedVersion) {
        return reopenConversation(support, conversationId, expectedVersion, "Support follow-up");
    }

    @Transactional
    public ChatConversationDTO closeConversation(
            CustomUserDetails support, Long conversationId, Long expectedVersion, String reason) {
        authorizationService.requirePermission(support, ActionCode.CREATE);
        SupportConversation conversation = requireAccessibleConversation(support, conversationId, "CLOSE");
        assertExpectedVersion(conversation, expectedVersion);
        if ("CLOSED".equals(conversation.getStatus())) return toConversation(conversation);
        assertLifecycleOwner(support, conversation, "CLOSE");
        String normalizedReason = normalizeLifecycleReason(reason);
        Instant now = Instant.now();
        conversation.setStatus("CLOSED");
        conversation.setClosedAt(now);
        conversation.setClosedReason(normalizedReason);
        conversation.setLastActivityAt(now);
        conversation.setSlaDeadlineAt(null);
        conversationRepository.saveAndFlush(conversation);
        auditService.record(conversation, support.getUserId(), "CLOSED", normalizedReason);
        return toConversation(conversation);
    }

    @Transactional
    public ChatConversationDTO reopenConversation(
            CustomUserDetails support, Long conversationId, Long expectedVersion, String reason) {
        authorizationService.requirePermission(support, ActionCode.CREATE);
        SupportConversation conversation = requireAccessibleConversation(support, conversationId, "REOPEN");
        assertExpectedVersion(conversation, expectedVersion);
        if (!"CLOSED".equals(conversation.getStatus()) && !"ESCALATED".equals(conversation.getStatus())) {
            if ("OPEN".equals(conversation.getStatus())) return toConversation(conversation);
            throw new IllegalStateException("Only closed or escalated conversations can be reopened.");
        }
        conversation.setAssignedAgentId(null);
        conversation.setAssignedAt(null);
        conversation.setEscalatedAt(null);
        conversation.setClosedAt(null);
        conversation.setStatus("OPEN");
        String normalizedReason = normalizeLifecycleReason(reason);
        Instant now = Instant.now();
        conversation.setReopenedAt(now);
        conversation.setReopenReason(normalizedReason);
        conversation.setLastActivityAt(now);
        conversation.setSlaDeadlineAt(nextSlaDeadline());
        conversationRepository.saveAndFlush(conversation);
        auditService.record(conversation, support.getUserId(), "REOPENED", normalizedReason);
        return toConversation(conversation);
    }

    @Transactional(readOnly = true)
    public ChatPageDTO<ChatConversationDTO> getMyConversations(
            CustomUserDetails customer, int page, int size) {
        Page<SupportConversation> result = conversationRepository
                .findByCustomerIdAndUpdatedAtGreaterThanEqualOrderByUpdatedAtDescIdDesc(
                        customer.getUserId(), cutoff(), safePage(page, size));
        return page(result.map(this::toConversation));
    }

    @Transactional(readOnly = true)
    public ChatPageDTO<ChatMessageDTO> getMyConversationMessages(
            CustomUserDetails customer, Long conversationId, int page, int size) {
        requireOwnedConversation(conversationId, customer.getUserId());
        return messagePage(conversationId, page, size);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDTO> getMyHistory(CustomUserDetails userDetails) {
        return conversationRepository.findFirstByCustomerIdOrderByUpdatedAtDesc(userDetails.getUserId())
                .map(conversation -> messagePage(conversation.getId(), 0, 100).content())
                .orElseGet(() -> mapLegacyHistory(userDetails.getUserId()));
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDTO> getSupportHistory(CustomUserDetails support, Long conversationId) {
        authorizationService.requirePermission(support, ActionCode.VIEW);
        requireAccessibleConversation(support, conversationId, "HISTORY");
        return messagePage(conversationId, 0, 100).content();
    }

    @Transactional(readOnly = true)
    public ChatPageDTO<ChatMessageDTO> getSupportConversationMessages(
            CustomUserDetails support, Long conversationId, int page, int size) {
        authorizationService.requirePermission(support, ActionCode.VIEW);
        requireAccessibleConversation(support, conversationId, "HISTORY");
        return messagePage(conversationId, page, size);
    }

    @Transactional(readOnly = true)
    public List<ChatConversationDTO> getSupportConversations(CustomUserDetails support) {
        return getSupportConversations(support, null, "ALL", "ALL", null, null);
    }

    @Transactional(readOnly = true)
    public List<ChatConversationDTO> getSupportConversations(
            CustomUserDetails support,
            String status,
            String assignment,
            String sla,
            Long requestedHotelId) {
        return getSupportConversations(support, status, assignment, sla, requestedHotelId, null);
    }

    @Transactional(readOnly = true)
    public List<ChatConversationDTO> getSupportConversations(
            CustomUserDetails support,
            String status,
            String assignment,
            String sla,
            Long requestedHotelId,
            String query) {
        authorizationService.requirePermission(support, ActionCode.VIEW);
        String normalizedStatus = normalizeOptionalFilter(status, QUEUE_STATUSES, "status");
        String normalizedAssignment = normalizeFilter(assignment, ASSIGNMENT_FILTERS, "assignment");
        String normalizedSla = normalizeFilter(sla, SLA_FILTERS, "sla");
        Set<Long> hotelIds = accessibleHotelIds(support.getUserId());
        boolean systemAdministrator = authorizationService.isSystemAdministrator(support);
        if (requestedHotelId != null && !systemAdministrator && !hotelIds.contains(requestedHotelId)) {
            throw new ResourceNotFoundException("Support queue was not found.");
        }
        List<SupportConversation> source = systemAdministrator
                ? conversationRepository.findAllByOrderByLastActivityAtDesc()
                : hotelIds.isEmpty() ? List.of()
                : conversationRepository.findByHotelIdInOrderByLastActivityAtDesc(hotelIds);
        Instant retentionCutoff = cutoff();
        String normalizedQuery = normalizeSearchQuery(query);
        return source.stream()
                .filter(item -> item.getUpdatedAt() == null || !item.getUpdatedAt().isBefore(retentionCutoff))
                .filter(item -> normalizedStatus == null
                        ? !"CLOSED".equals(item.getStatus()) : normalizedStatus.equals(item.getStatus()))
                .filter(item -> requestedHotelId == null || requestedHotelId.equals(item.getHotelId()))
                .filter(item -> matchesAssignment(item, normalizedAssignment, support.getUserId()))
                .filter(item -> "ALL".equals(normalizedSla) || normalizedSla.equals(slaState(item)))
                .map(this::toConversation)
                .filter(item -> matchesSearch(item, normalizedQuery))
                .toList();
    }

    @Transactional(readOnly = true)
    public String getUsername(Long userId) {
        return requireUser(userId).getUsername();
    }

    @Transactional(readOnly = true)
    public Long getConversationCustomerId(Long conversationId) {
        return conversationRepository.findById(conversationId)
                .map(SupportConversation::getCustomerId)
                .orElseThrow(() -> new ResourceNotFoundException("Support conversation not found."));
    }

    @Transactional(readOnly = true)
    public List<String> getSupportRecipients(Long hotelId) {
        return hotelId == null
                ? userRepository.findSystemAdministratorUsernames()
                : userRepository.findSupportRecipientUsernames(hotelId);
    }

    @Transactional
    public ChatMessageDTO acknowledgeMessage(
            CustomUserDetails actor, Long messageId, String requestedState) {
        ChatMessage message = chatMessageRepository.findLockedById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat message was not found."));
        SupportConversation conversation = conversationRepository.findById(message.getConversationId())
                .orElseThrow(() -> new ResourceNotFoundException("Chat message was not found."));
        authorizeAcknowledgement(actor, message, conversation);
        String state = normalizeDeliveryState(requestedState);
        Instant now = Instant.now();
        String previousState = message.getDeliveryStatus();
        if ("READ".equals(state)) {
            if (message.getDeliveredAt() == null) message.setDeliveredAt(now);
            if (message.getReadAt() == null) message.setReadAt(now);
            message.setRead(true);
            message.setDeliveryStatus("READ");
        } else if ("PERSISTED".equals(message.getDeliveryStatus())) {
            message.setDeliveredAt(now);
            message.setDeliveryStatus("DELIVERED");
        }
        ChatMessageDTO saved = mapToDTO(chatMessageRepository.saveAndFlush(message));
        if (!state.equals(previousState)) {
            auditService.record(conversation, actor.getUserId(), "MESSAGE_" + state,
                    "Message " + message.getId() + " changed from " + previousState + " to " + state);
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<com.hotel.dtos.SupportConversationEventDTO> supportAuditHistory(
            Long conversationId, int page, int size) {
        return auditService.history(conversationId, page, size);
    }

    public java.util.Map<String, Object> supportAuditPolicy() { return auditService.policy(); }

    private ChatMessageDTO persistCustomerMessage(
            CustomUserDetails sender,
            SupportConversation conversation,
            String content,
            String clientMessageId) {
        String normalizedContent = normalizeContent(content);
        MessageWrite write = writeMessage(
                conversation,
                sender.getUserId(),
                0L,
                normalizedContent,
                clientMessageId);
        if (!write.created()) return write.message();
        if ("CLOSED".equals(conversation.getStatus())) {
            throw new IllegalStateException("Closed conversations must be reopened before sending another message.");
        }
        Instant now = Instant.now();
        conversation.setLastActivityAt(now);
        conversation.setLastCustomerMessageAt(now);
        conversation.setSlaDeadlineAt(now.plus(slaResponseMinutes, ChronoUnit.MINUTES));
        conversationRepository.saveAndFlush(conversation);
        auditService.record(conversation, sender.getUserId(), "CUSTOMER_MESSAGE", "Customer message queued for support");
        return write.message();
    }

    private SupportConversation createEntity(Long customerId, String subject, CustomerContext context) {
        SupportConversation conversation = new SupportConversation();
        conversation.setPublicId(UUID.randomUUID().toString());
        conversation.setCustomerId(customerId);
        conversation.setSubject(subject);
        conversation.setHotel(context.hotel());
        conversation.setReservation(context.reservation());
        conversation.setChannel("IN_APP");
        conversation.setStatus("OPEN");
        conversation.setLastActivityAt(Instant.now());
        return conversationRepository.saveAndFlush(conversation);
    }

    private SupportConversation requireOwnedConversation(Long conversationId, Long customerId) {
        return conversationRepository.findByIdAndCustomerId(conversationId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Support conversation not found."));
    }

    private SupportConversation requireAccessibleConversation(
            CustomUserDetails support, Long conversationId, String action) {
        if (conversationId == null) {
            throw new ResourceNotFoundException("Support conversation not found.");
        }
        SupportConversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Support conversation not found."));
        assertAccessibleConversation(support, conversation, action);
        return conversation;
    }

    private void assertAccessibleConversation(
            CustomUserDetails support, SupportConversation conversation, String action) {
        if (!authorizationService.isSystemAdministrator(support)
                && (conversation.getHotelId() == null
                || !accessibleHotelIds(support.getUserId()).contains(conversation.getHotelId()))) {
            auditService.recordDenied(
                    conversation,
                    support.getUserId(),
                    "ACCESS_DENIED_" + action,
                    "Actor is outside the conversation tenant");
            throw new ResourceNotFoundException("Support conversation not found.");
        }
    }

    private SupportConversation requireLockedConversation(Long conversationId) {
        if (conversationId == null) {
            throw new ResourceNotFoundException("Support conversation not found.");
        }
        return conversationRepository.findLockedById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Support conversation not found."));
    }

    private SupportConversation lockConversationForIdempotentSend(
            SupportConversation conversation, String clientMessageId) {
        if (clientMessageId == null) return conversation;
        return conversationRepository.findLockedById(conversation.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Support conversation not found."));
    }

    private MessageWrite writeMessage(
            SupportConversation conversation,
            Long senderId,
            Long receiverId,
            String content,
            String clientMessageId) {
        if (clientMessageId != null) {
            ChatMessageIdempotencyWriter.WriteResult result = messageWriter.createOrLoad(
                    conversation, senderId, receiverId, clientMessageId, content);
            return new MessageWrite(mapToDTO(result.message()), result.created());
        }
        ChatMessage entity = new ChatMessage();
        entity.setConversationId(conversation.getId());
        entity.setHotelId(conversation.getHotelId());
        entity.setLegacyUnscoped(conversation.getHotelId() == null);
        entity.setSenderId(senderId);
        entity.setReceiverId(receiverId);
        entity.setContent(content);
        entity.setDeliveryStatus("PERSISTED");
        return new MessageWrite(mapToDTO(chatMessageRepository.save(entity)), true);
    }

    private ChatPageDTO<ChatMessageDTO> messagePage(Long conversationId, int page, int size) {
        Page<ChatMessage> result = chatMessageRepository
                .findByConversationIdAndTimestampGreaterThanEqualOrderByTimestampDescIdDesc(
                        conversationId, cutoff(), safePage(page, size));
        List<ChatMessageDTO> chronological = new ArrayList<>(result.stream().map(this::mapToDTO).toList());
        chronological.sort(Comparator.comparing(ChatMessageDTO::getTimestamp));
        return new ChatPageDTO<>(chronological, result.getTotalElements(), result.getTotalPages(),
                result.getNumber(), result.getSize(), result.isFirst(), result.isLast(), retentionDays);
    }

    private List<ChatMessageDTO> mapLegacyHistory(Long customerId) {
        return chatMessageRepository.findCustomerSupportHistory(customerId).stream()
                .filter(message -> message.getTimestamp() == null || !message.getTimestamp().isBefore(cutoff()))
                .map(this::mapToDTO)
                .toList();
    }

    private ChatConversationDTO toConversation(SupportConversation conversation) {
        User customer = requireUser(conversation.getCustomerId());
        ChatMessage lastMessage = chatMessageRepository
                .findFirstByConversationIdOrderByTimestampDesc(conversation.getId()).orElse(null);
        String customerName = displayName(customer);
        User assignedAgent = conversation.getAssignedAgentId() == null
                ? null : userRepository.findById(conversation.getAssignedAgentId()).orElse(null);
        Hotel hotel = conversation.getHotelId() == null
                ? null : hotelRepository.findById(conversation.getHotelId()).orElse(null);
        return new ChatConversationDTO(
                conversation.getId(),
                conversation.getCustomerId(),
                customerName,
                conversation.getSubject(),
                conversation.getHotelId(),
                hotel == null ? null : hotel.getName(),
                conversation.getReservationId(),
                conversation.getAssignedAgentId(),
                assignedAgent == null ? null : displayName(assignedAgent),
                conversation.getStatus(),
                conversation.getVersion(),
                conversation.getSlaDeadlineAt(),
                slaState(conversation),
                conversation.getCreatedAt(),
                conversation.getLastActivityAt(),
                conversation.getAssignedAt(),
                conversation.getEscalatedAt(),
                conversation.getClosedAt(),
                conversation.getFirstResponseAt(),
                conversation.getLastCustomerMessageAt(),
                conversation.getLastSupportReplyAt(),
                conversation.getClosedReason(),
                conversation.getReopenedAt(),
                conversation.getReopenReason(),
                lastMessage == null ? "" : lastMessage.getContent(),
                lastMessage == null ? conversation.getLastActivityAt() : lastMessage.getTimestamp());
    }

    private ChatMessageDTO mapToDTO(ChatMessage entity) {
        ChatMessageDTO dto = new ChatMessageDTO();
        dto.setId(entity.getId());
        dto.setConversationId(entity.getConversationId());
        dto.setHotelId(entity.getHotelId());
        dto.setSenderId(entity.getSenderId());
        dto.setReceiverId(entity.getReceiverId());
        dto.setClientMessageId(entity.getClientMessageId());
        dto.setContent(entity.getContent());
        dto.setTimestamp(entity.getTimestamp());
        dto.setRead(entity.isRead());
        dto.setDeliveryStatus(entity.getDeliveryStatus());
        dto.setDeliveredAt(entity.getDeliveredAt());
        dto.setReadAt(entity.getReadAt());
        return dto;
    }

    private void authorizeAcknowledgement(
            CustomUserDetails actor,
            ChatMessage message,
            SupportConversation conversation) {
        if (actor.getUserId().equals(message.getSenderId())) {
            throw new ResourceNotFoundException("Chat message was not found.");
        }
        if (actor.getUserId().equals(conversation.getCustomerId())) {
            if (!actor.getUserId().equals(message.getReceiverId())) {
                throw new ResourceNotFoundException("Chat message was not found.");
            }
            return;
        }
        if (!authorizationService.hasPermission(actor, ActionCode.VIEW)) {
            throw new ResourceNotFoundException("Chat message was not found.");
        }
        requireAccessibleConversation(actor, conversation.getId(), "ACKNOWLEDGE");
    }

    private void assignForReply(SupportConversation conversation, CustomUserDetails support) {
        if ("CLOSED".equals(conversation.getStatus())) {
            throw new IllegalStateException("Closed conversations cannot receive replies.");
        }
        Long assignedAgentId = conversation.getAssignedAgentId();
        if (assignedAgentId == null || "ESCALATED".equals(conversation.getStatus())) {
            conversation.setAssignedAgentId(requireUser(support.getUserId()).getId());
            conversation.setAssignedAt(Instant.now());
            conversation.setStatus("ASSIGNED");
            return;
        }
        if (!assignedAgentId.equals(support.getUserId())
                && !authorizationService.isSystemAdministrator(support)) {
            auditService.recordDenied(
                    conversation, support.getUserId(), "ACCESS_DENIED_REPLY", "Conversation assigned to another agent");
            throw new AccessDeniedException("Conversation is assigned to another support agent");
        }
    }

    private void assertLifecycleOwner(
            CustomUserDetails support, SupportConversation conversation, String action) {
        if (conversation.getAssignedAgentId() != null
                && !conversation.getAssignedAgentId().equals(support.getUserId())
                && !authorizationService.isSystemAdministrator(support)) {
            auditService.recordDenied(
                    conversation, support.getUserId(), "ACCESS_DENIED_" + action,
                    "Conversation assigned to another agent");
            throw new AccessDeniedException("Only the assigned agent can change this conversation lifecycle");
        }
    }

    private CustomerContext resolveCustomerContext(Long customerId, Long requestedHotelId, Long reservationId) {
        User customer = requireUser(customerId);
        if (reservationId != null) {
            Reservation reservation = reservationRepository.findById(reservationId)
                    .filter(item -> item.getUser().getId().equals(customerId))
                    .orElseThrow(() -> new ResourceNotFoundException("Reservation was not found."));
            if (requestedHotelId != null && !reservation.getHotel().getId().equals(requestedHotelId)) {
                throw new ResourceNotFoundException("Reservation was not found.");
            }
            return new CustomerContext(reservation.getHotel(), reservation);
        }
        if (requestedHotelId != null) {
            return new CustomerContext(requireOperationalHotel(requestedHotelId), null);
        }
        Reservation latestReservation = reservationRepository.findByUserIdOrderByIdDesc(customerId)
                .stream().findFirst().orElse(null);
        if (latestReservation != null) {
            return new CustomerContext(latestReservation.getHotel(), latestReservation);
        }
        if (customer.getHotel() != null && isOperational(customer.getHotel())) {
            return new CustomerContext(customer.getHotel(), null);
        }
        return new CustomerContext(null, null);
    }

    private Hotel requireOperationalHotel(Long hotelId) {
        return hotelRepository.findById(hotelId)
                .filter(this::isOperational)
                .orElseThrow(() -> new ResourceNotFoundException("Property was not found."));
    }

    private boolean isOperational(Hotel hotel) {
        return "APPROVED".equalsIgnoreCase(hotel.getApprovalStatus())
                && "ACTIVE".equalsIgnoreCase(hotel.getOperationStatus());
    }

    private Set<Long> accessibleHotelIds(Long userId) {
        Set<Long> hotelIds = new LinkedHashSet<>();
        for (UserProperty assignment : safeList(userPropertyRepository.findByUserId(userId))) {
            if ("ACTIVE".equals(assignment.getStatus()) && assignment.getHotel() != null) {
                hotelIds.add(assignment.getHotel().getId());
            }
        }
        userRepository.findById(userId)
                .map(User::getHotel)
                .map(Hotel::getId)
                .ifPresent(hotelIds::add);
        return hotelIds;
    }

    private void assertExpectedVersion(SupportConversation conversation, Long expectedVersion) {
        if (expectedVersion != null && !Objects.equals(expectedVersion, conversation.getVersion())) {
            throw new OptimisticLockingFailureException(
                    "Support conversation changed. Reload the queue before retrying.");
        }
    }

    private boolean matchesAssignment(
            SupportConversation conversation, String assignment, Long supportUserId) {
        return switch (assignment) {
            case "UNASSIGNED" -> conversation.getAssignedAgentId() == null;
            case "MINE" -> supportUserId.equals(conversation.getAssignedAgentId());
            default -> true;
        };
    }

    private String slaState(SupportConversation conversation) {
        Instant deadline = conversation.getSlaDeadlineAt();
        if (deadline == null) return "NO_PENDING_RESPONSE";
        Instant now = Instant.now();
        if (!deadline.isAfter(now)) return "BREACHED";
        return Duration.between(now, deadline).toMinutes() <= slaAtRiskMinutes ? "AT_RISK" : "ON_TRACK";
    }

    private boolean matchesSearch(ChatConversationDTO conversation, String query) {
        if (query == null) return true;
        return java.util.stream.Stream.of(
                        conversation.getSubject(),
                        conversation.getCustomerName(),
                        conversation.getHotelName(),
                        conversation.getLastMessage(),
                        conversation.getReservationId() == null ? null : conversation.getReservationId().toString(),
                        conversation.getConversationId() == null ? null : conversation.getConversationId().toString())
                .filter(Objects::nonNull)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.contains(query));
    }

    private String normalizeSearchQuery(String query) {
        if (query == null || query.isBlank()) return null;
        String normalized = query.strip().toLowerCase(Locale.ROOT);
        if (normalized.length() > 120) throw new IllegalArgumentException("Search query is too long.");
        return normalized;
    }

    private String normalizeLifecycleReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Lifecycle reason is required.");
        }
        String normalized = reason.strip();
        if (normalized.length() > 500) {
            throw new IllegalArgumentException("Lifecycle reason must not exceed 500 characters.");
        }
        return normalized;
    }

    private String normalizeOptionalFilter(String value, Set<String> allowed, String name) {
        if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value)) return null;
        return normalizeFilter(value, allowed, name);
    }

    private String normalizeFilter(String value, Set<String> allowed, String name) {
        String normalized = value == null || value.isBlank() ? "ALL" : value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported support queue " + name + " filter.");
        }
        return normalized;
    }

    private String displayName(User user) {
        return user.getFullName() == null || user.getFullName().isBlank()
                ? user.getUsername() : user.getFullName();
    }

    private PageRequest safePage(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
    }

    private ChatPageDTO<ChatConversationDTO> page(Page<ChatConversationDTO> page) {
        return new ChatPageDTO<>(page.getContent(), page.getTotalElements(), page.getTotalPages(),
                page.getNumber(), page.getSize(), page.isFirst(), page.isLast(), retentionDays);
    }

    private Instant cutoff() {
        return Instant.now().minus(retentionDays, ChronoUnit.DAYS);
    }

    private Instant nextSlaDeadline() {
        return Instant.now().plus(slaResponseMinutes, ChronoUnit.MINUTES);
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Support conversation not found."));
    }

    private String normalizeSubject(String subject) {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Conversation subject is required.");
        }
        String normalized = subject.trim();
        if (normalized.length() > 120) {
            throw new IllegalArgumentException("Conversation subject exceeds 120 characters.");
        }
        return normalized;
    }

    private String normalizeContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Message content is required.");
        }
        String normalized = content.trim();
        if (normalized.length() > 2000) {
            throw new IllegalArgumentException("Message content exceeds 2000 characters.");
        }
        return normalized;
    }

    private String normalizeClientMessageId(String clientMessageId) {
        if (clientMessageId == null || clientMessageId.isBlank()) return null;
        String normalized = clientMessageId.trim();
        if (normalized.length() > 64 || !normalized.matches("[A-Za-z0-9._:-]+")) {
            throw new IllegalArgumentException("Client message id is invalid.");
        }
        return normalized;
    }

    private String normalizeDeliveryState(String state) {
        String normalized = state == null ? "" : state.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("DELIVERED", "READ").contains(normalized)) {
            throw new IllegalArgumentException("Unsupported chat delivery state.");
        }
        return normalized;
    }

    private <T> List<T> safeList(Collection<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private record CustomerContext(Hotel hotel, Reservation reservation) {
    }

    private record MessageWrite(ChatMessageDTO message, boolean created) {
    }
}
