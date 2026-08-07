package com.hotel.services;

import com.hotel.dtos.ChatMessageDTO;
import com.hotel.entities.ChatMessage;
import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.entities.SupportConversation;
import com.hotel.entities.User;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock private ChatMessageRepository chatMessageRepository;
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
                conversationRepository,
                userRepository,
                userPropertyRepository,
                reservationRepository,
                hotelRepository,
                new ChatAuthorizationService(),
                auditService);
    }

    @Test
    void sendToSupportDerivesTenantFromAuthenticatedCustomersReservation() {
        User customerEntity = userEntity(42L, "customer");
        Hotel hotel = hotel(11L, "Tenant Hotel");
        Reservation reservation = reservation(31L, customerEntity, hotel);
        CustomUserDetails customer = user(42L, Map.of(), "CUSTOMER");

        when(userRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(customerEntity));
        when(reservationRepository.findByUserIdOrderByIdDesc(42L)).thenReturn(List.of(reservation));
        when(conversationRepository.findFirstByCustomerIdAndHotelIdAndChannelAndStatusInOrderByLastActivityAtDesc(
                42L, 11L, "IN_APP", Set.of("OPEN", "ASSIGNED", "ESCALATED")))
                .thenReturn(Optional.empty());
        when(conversationRepository.save(any(SupportConversation.class))).thenAnswer(invocation -> {
            SupportConversation conversation = invocation.getArgument(0);
            conversation.setId(71L);
            return conversation;
        });
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            message.setId(99L);
            message.setTimestamp(Instant.parse("2026-07-31T10:00:00Z"));
            return message;
        });

        ChatMessageDTO result = chatService.sendToSupport(customer, null, null, "  Xin chao  ");

        assertEquals(71L, result.getConversationId());
        assertEquals(11L, result.getHotelId());
        assertEquals(42L, result.getSenderId());
        assertEquals(0L, result.getReceiverId());
        assertEquals("Xin chao", result.getContent());
    }

    @Test
    void supportReplyRequiresAiChatCreatePermission() {
        CustomUserDetails nonSupport = user(7L, Map.of(), "CUSTOMER");

        assertThrows(AccessDeniedException.class,
                () -> chatService.replyToCustomer(nonSupport, 71L, "Phan hoi"));

        verify(chatMessageRepository, never()).save(any(ChatMessage.class));
    }

    @Test
    void customerWithoutPropertyContextCannotCreateUnscopedConversation() {
        User customerEntity = userEntity(42L, "customer");
        when(userRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(customerEntity));
        when(reservationRepository.findByUserIdOrderByIdDesc(42L)).thenReturn(List.of());

        assertThrows(IllegalStateException.class,
                () -> chatService.sendToSupport(user(42L, Map.of(), "CUSTOMER"), null, null, "Help"));

        verify(chatMessageRepository, never()).save(any(ChatMessage.class));
    }

    @Test
    void myHistoryUsesLatestPrincipalConversation() {
        SupportConversation conversation = new SupportConversation();
        conversation.setId(71L);
        when(conversationRepository.findFirstByCustomerIdOrderByLastActivityAtDesc(42L))
                .thenReturn(Optional.of(conversation));
        when(chatMessageRepository.findByConversationIdAndLegacyUnscopedFalseOrderByTimestampAsc(71L))
                .thenReturn(List.of());

        chatService.getMyHistory(user(42L, Map.of(), "CUSTOMER"));

        verify(chatMessageRepository).findByConversationIdAndLegacyUnscopedFalseOrderByTimestampAsc(71L);
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

    private User userEntity(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash("hash");
        user.setStatus("ACTIVE");
        return user;
    }

    private Hotel hotel(Long id, String name) {
        Hotel hotel = new Hotel();
        hotel.setId(id);
        hotel.setName(name);
        hotel.setAddressLine("Address");
        hotel.setCity("City");
        hotel.setCountry("VN");
        return hotel;
    }

    private Reservation reservation(Long id, User customer, Hotel hotel) {
        Reservation reservation = new Reservation();
        reservation.setId(id);
        reservation.setUser(customer);
        reservation.setHotel(hotel);
        reservation.setCheckInDate(LocalDate.of(2026, 8, 1));
        reservation.setCheckOutDate(LocalDate.of(2026, 8, 2));
        reservation.setGuests(1);
        reservation.setTotalAmount(BigDecimal.valueOf(500_000));
        reservation.setStatus("CONFIRMED");
        return reservation;
    }
}
