package com.hotel.services;

import com.hotel.dtos.ChatConversationDTO;
import com.hotel.dtos.ChatMessageDTO;
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
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatService {

    private static final Set<String> ACTIVE_STATUSES = Set.of("OPEN", "ASSIGNED", "ESCALATED");

    private final ChatMessageRepository chatMessageRepository;
    private final SupportConversationRepository conversationRepository;
    private final UserRepository userRepository;
    private final UserPropertyRepository userPropertyRepository;
    private final ReservationRepository reservationRepository;
    private final HotelRepository hotelRepository;
    private final ChatAuthorizationService authorizationService;
    private final SupportConversationAuditService auditService;

    @Transactional
    public ChatMessageDTO sendToSupport(
            CustomUserDetails sender,
            Long requestedHotelId,
            Long reservationId,
            String content) {
        CustomerContext context = resolveCustomerContext(sender.getUserId(), requestedHotelId, reservationId);
        SupportConversation conversation = conversationRepository
                .findFirstByCustomerIdAndHotelIdAndChannelAndStatusInOrderByLastActivityAtDesc(
                        sender.getUserId(),
                        context.hotel().getId(),
                        "IN_APP",
                        ACTIVE_STATUSES)
                .orElseGet(() -> createConversation(context.customer(), context.hotel(), context.reservation()));
        if (conversation.getReservation() == null && context.reservation() != null) {
            conversation.setReservation(context.reservation());
        }
        conversation.setLastActivityAt(Instant.now());
        conversationRepository.save(conversation);
        return saveMessage(conversation, sender.getUserId(), 0L, normalizeContent(content));
    }

    @Transactional
    public ChatMessageDTO replyToCustomer(CustomUserDetails support, Long conversationId, String content) {
        authorizationService.requirePermission(support, ActionCode.CREATE);
        SupportConversation conversation = requireAccessibleConversation(support, conversationId, "REPLY");
        assignForReply(conversation, support);
        conversation.setLastActivityAt(Instant.now());
        conversationRepository.save(conversation);
        ChatMessageDTO message = saveMessage(
                conversation,
                support.getUserId(),
                conversation.getCustomer().getId(),
                normalizeContent(content));
        auditService.record(conversation, support.getUserId(), "REPLIED", "Support reply accepted");
        return message;
    }

    @Transactional
    public ChatConversationDTO claimConversation(CustomUserDetails support, Long conversationId) {
        authorizationService.requirePermission(support, ActionCode.CREATE);
        SupportConversation conversation = requireAccessibleConversation(support, conversationId, "ASSIGN");
        User agent = requireUser(support.getUserId());
        if (conversation.getAssignedAgent() != null
                && !conversation.getAssignedAgent().getId().equals(agent.getId())
                && !authorizationService.isSystemAdministrator(support)) {
            auditService.recordDenied(conversation, support.getUserId(), "ACCESS_DENIED_ASSIGN", "Conversation already assigned");
            throw new AccessDeniedException("Conversation is assigned to another support agent");
        }
        conversation.setAssignedAgent(agent);
        conversation.setAssignedAt(Instant.now());
        conversation.setStatus("ASSIGNED");
        conversationRepository.save(conversation);
        auditService.record(conversation, support.getUserId(), "ASSIGNED", "Conversation assigned to support agent");
        return toConversation(conversation);
    }

    @Transactional
    public ChatConversationDTO escalateConversation(CustomUserDetails support, Long conversationId) {
        authorizationService.requirePermission(support, ActionCode.CREATE);
        SupportConversation conversation = requireAccessibleConversation(support, conversationId, "ESCALATE");
        if (conversation.getAssignedAgent() != null
                && !conversation.getAssignedAgent().getId().equals(support.getUserId())
                && !authorizationService.isSystemAdministrator(support)) {
            auditService.recordDenied(conversation, support.getUserId(), "ACCESS_DENIED_ESCALATE", "Conversation assigned to another agent");
            throw new AccessDeniedException("Only the assigned agent can escalate this conversation");
        }
        conversation.setStatus("ESCALATED");
        conversation.setEscalatedAt(Instant.now());
        conversation.setLastActivityAt(Instant.now());
        conversationRepository.save(conversation);
        auditService.record(conversation, support.getUserId(), "ESCALATED", "Conversation returned to the tenant queue");
        return toConversation(conversation);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDTO> getMyHistory(CustomUserDetails userDetails) {
        return conversationRepository.findFirstByCustomerIdOrderByLastActivityAtDesc(userDetails.getUserId())
                .map(this::mapHistory)
                .orElseGet(List::of);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDTO> getSupportHistory(CustomUserDetails support, Long conversationId) {
        authorizationService.requirePermission(support, ActionCode.VIEW);
        return mapHistory(requireAccessibleConversation(support, conversationId, "HISTORY"));
    }

    @Transactional(readOnly = true)
    public List<ChatConversationDTO> getSupportConversations(CustomUserDetails support) {
        authorizationService.requirePermission(support, ActionCode.VIEW);
        List<SupportConversation> conversations;
        if (authorizationService.isSystemAdministrator(support)) {
            conversations = conversationRepository.findByStatusNotOrderByLastActivityAtDesc("CLOSED");
        } else {
            Set<Long> hotelIds = accessibleHotelIds(support.getUserId());
            conversations = hotelIds.isEmpty()
                    ? List.of()
                    : conversationRepository.findByHotelIdInAndStatusNotOrderByLastActivityAtDesc(hotelIds, "CLOSED");
        }
        return conversations.stream().map(this::toConversation).toList();
    }

    @Transactional(readOnly = true)
    public String getUsername(Long userId) {
        return requireUser(userId).getUsername();
    }

    @Transactional(readOnly = true)
    public List<String> getSupportRecipients(Long hotelId) {
        return userRepository.findSupportRecipientUsernames(hotelId);
    }

    private SupportConversation createConversation(User customer, Hotel hotel, Reservation reservation) {
        SupportConversation conversation = new SupportConversation();
        conversation.setPublicId(UUID.randomUUID().toString());
        conversation.setCustomer(customer);
        conversation.setHotel(hotel);
        conversation.setReservation(reservation);
        conversation.setChannel("IN_APP");
        conversation.setStatus("OPEN");
        conversation.setLastActivityAt(Instant.now());
        return conversationRepository.save(conversation);
    }

    private ChatMessageDTO saveMessage(
            SupportConversation conversation,
            Long senderId,
            Long receiverId,
            String content) {
        ChatMessage entity = new ChatMessage();
        entity.setConversation(conversation);
        entity.setHotel(conversation.getHotel());
        entity.setLegacyUnscoped(false);
        entity.setSenderId(senderId);
        entity.setReceiverId(receiverId);
        entity.setContent(content);
        return mapToDTO(chatMessageRepository.save(entity));
    }

    private List<ChatMessageDTO> mapHistory(SupportConversation conversation) {
        return chatMessageRepository
                .findByConversationIdAndLegacyUnscopedFalseOrderByTimestampAsc(conversation.getId())
                .stream()
                .map(this::mapToDTO)
                .toList();
    }

    private ChatConversationDTO toConversation(SupportConversation conversation) {
        List<ChatMessage> history = chatMessageRepository
                .findByConversationIdAndLegacyUnscopedFalseOrderByTimestampAsc(conversation.getId());
        ChatMessage lastMessage = history.isEmpty() ? null : history.get(history.size() - 1);
        User customer = conversation.getCustomer();
        String customerName = customer.getFullName() == null || customer.getFullName().isBlank()
                ? customer.getUsername()
                : customer.getFullName();
        return new ChatConversationDTO(
                conversation.getId(),
                customer.getId(),
                customerName,
                conversation.getHotel().getId(),
                conversation.getHotel().getName(),
                conversation.getReservation() == null ? null : conversation.getReservation().getId(),
                conversation.getAssignedAgent() == null ? null : conversation.getAssignedAgent().getId(),
                conversation.getStatus(),
                lastMessage == null ? "" : lastMessage.getContent(),
                lastMessage == null ? conversation.getLastActivityAt() : lastMessage.getTimestamp());
    }

    private SupportConversation requireAccessibleConversation(
            CustomUserDetails support,
            Long conversationId,
            String action) {
        if (conversationId == null) {
            throw new ResourceNotFoundException("Support conversation was not found.");
        }
        SupportConversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Support conversation was not found."));
        if (!authorizationService.isSystemAdministrator(support)
                && !accessibleHotelIds(support.getUserId()).contains(conversation.getHotel().getId())) {
            auditService.recordDenied(
                    conversation,
                    support.getUserId(),
                    "ACCESS_DENIED_" + action,
                    "Actor is outside the conversation tenant");
            throw new ResourceNotFoundException("Support conversation was not found.");
        }
        return conversation;
    }

    private void assignForReply(SupportConversation conversation, CustomUserDetails support) {
        if ("CLOSED".equals(conversation.getStatus())) {
            throw new IllegalStateException("Closed conversations cannot receive replies.");
        }
        User assigned = conversation.getAssignedAgent();
        if (assigned == null || "ESCALATED".equals(conversation.getStatus())) {
            conversation.setAssignedAgent(requireUser(support.getUserId()));
            conversation.setAssignedAt(Instant.now());
            conversation.setStatus("ASSIGNED");
            return;
        }
        if (!assigned.getId().equals(support.getUserId())
                && !authorizationService.isSystemAdministrator(support)) {
            auditService.recordDenied(conversation, support.getUserId(), "ACCESS_DENIED_REPLY", "Conversation assigned to another agent");
            throw new AccessDeniedException("Conversation is assigned to another support agent");
        }
    }

    private CustomerContext resolveCustomerContext(Long customerId, Long requestedHotelId, Long reservationId) {
        User customer = userRepository.findByIdForUpdate(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Support conversation was not found."));
        if (reservationId != null) {
            Reservation reservation = reservationRepository.findById(reservationId)
                    .filter(item -> item.getUser().getId().equals(customerId))
                    .orElseThrow(() -> new ResourceNotFoundException("Reservation was not found."));
            if (requestedHotelId != null && !reservation.getHotel().getId().equals(requestedHotelId)) {
                throw new ResourceNotFoundException("Reservation was not found.");
            }
            return new CustomerContext(customer, reservation.getHotel(), reservation);
        }
        if (requestedHotelId != null) {
            Hotel hotel = hotelRepository.findById(requestedHotelId)
                    .orElseThrow(() -> new ResourceNotFoundException("Property was not found."));
            if (!"APPROVED".equalsIgnoreCase(hotel.getApprovalStatus())
                    || !"ACTIVE".equalsIgnoreCase(hotel.getOperationStatus())) {
                throw new ResourceNotFoundException("Property was not found.");
            }
            return new CustomerContext(customer, hotel, null);
        }
        Reservation latestReservation = reservationRepository.findByUserIdOrderByIdDesc(customerId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Choose a property before starting a tenant support conversation."));
        return new CustomerContext(customer, latestReservation.getHotel(), latestReservation);
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

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Support conversation was not found."));
    }

    private String normalizeContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Message content is required.");
        }
        String normalized = content.trim();
        if (normalized.length() > 2000) {
            throw new IllegalArgumentException("Message content cannot exceed 2,000 characters.");
        }
        return normalized;
    }

    private ChatMessageDTO mapToDTO(ChatMessage entity) {
        ChatMessageDTO dto = new ChatMessageDTO();
        dto.setId(entity.getId());
        dto.setConversationId(entity.getConversation().getId());
        dto.setHotelId(entity.getHotel().getId());
        dto.setSenderId(entity.getSenderId());
        dto.setReceiverId(entity.getReceiverId());
        dto.setContent(entity.getContent());
        dto.setTimestamp(entity.getTimestamp());
        dto.setRead(entity.isRead());
        return dto;
    }

    private <T> List<T> safeList(Collection<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private record CustomerContext(User customer, Hotel hotel, Reservation reservation) {
    }
}
