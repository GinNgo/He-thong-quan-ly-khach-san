package com.hotel.services;

import com.hotel.dtos.SupportAttachmentDTO;
import com.hotel.entities.SupportConversation;
import com.hotel.entities.SupportConversationAttachment;
import com.hotel.entities.UserProperty;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.exceptions.SupportAttachmentException;
import com.hotel.repositories.SupportConversationAttachmentRepository;
import com.hotel.repositories.SupportConversationRepository;
import com.hotel.repositories.UserPropertyRepository;
import com.hotel.security.ActionCode;
import com.hotel.security.ChatAuthorizationService;
import com.hotel.security.CustomUserDetails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class SupportAttachmentService {
    private final SupportConversationAttachmentRepository attachmentRepository;
    private final SupportConversationRepository conversationRepository;
    private final UserPropertyRepository userPropertyRepository;
    private final ChatAuthorizationService authorizationService;
    private final SupportConversationAuditService auditService;
    private final long maxBytes;

    public SupportAttachmentService(
            SupportConversationAttachmentRepository attachmentRepository,
            SupportConversationRepository conversationRepository,
            UserPropertyRepository userPropertyRepository,
            ChatAuthorizationService authorizationService,
            SupportConversationAuditService auditService,
            @Value("${app.chat.attachment-max-bytes:5242880}") long maxBytes) {
        this.attachmentRepository = attachmentRepository;
        this.conversationRepository = conversationRepository;
        this.userPropertyRepository = userPropertyRepository;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
        this.maxBytes = Math.min(Math.max(maxBytes, 1024), 10L * 1024 * 1024);
    }

    @Transactional
    public SupportAttachmentDTO uploadForCustomer(
            CustomUserDetails customer, Long conversationId, MultipartFile file) {
        SupportConversation conversation = conversationRepository.findByIdAndCustomerId(
                        conversationId, customer.getUserId())
                .orElseThrow(this::notFound);
        return store(customer, conversation, file);
    }

    @Transactional
    public SupportAttachmentDTO uploadForSupport(
            CustomUserDetails support, Long conversationId, MultipartFile file) {
        authorizationService.requirePermission(support, ActionCode.CREATE);
        return store(support, requireSupportConversation(support, conversationId, "ATTACH"), file);
    }

    @Transactional(readOnly = true)
    public List<SupportAttachmentDTO> listForCustomer(CustomUserDetails customer, Long conversationId) {
        SupportConversation conversation = conversationRepository.findByIdAndCustomerId(
                        conversationId, customer.getUserId())
                .orElseThrow(this::notFound);
        return list(conversation.getId());
    }

    @Transactional(readOnly = true)
    public List<SupportAttachmentDTO> listForSupport(CustomUserDetails support, Long conversationId) {
        authorizationService.requirePermission(support, ActionCode.VIEW);
        return list(requireSupportConversation(support, conversationId, "ATTACHMENTS").getId());
    }

    @Transactional(readOnly = true)
    public AttachmentContent download(CustomUserDetails actor, Long attachmentId) {
        SupportConversationAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(this::notFound);
        SupportConversation conversation = attachment.getConversation();
        if (!actor.getUserId().equals(conversation.getCustomerId())) {
            authorizationService.requirePermission(actor, ActionCode.VIEW);
            requireSupportConversation(actor, conversation.getId(), "ATTACHMENT_DOWNLOAD");
        }
        return new AttachmentContent(
                attachment.getOriginalFilename(), attachment.getContentType(),
                attachment.getChecksumSha256(), attachment.getContentBytes());
    }

    private SupportAttachmentDTO store(
            CustomUserDetails actor, SupportConversation conversation, MultipartFile file) {
        if ("CLOSED".equals(conversation.getStatus())) {
            throw new IllegalStateException("Closed conversations must be reopened before adding attachments.");
        }
        ValidatedAttachment validated = validate(file);
        SupportConversationAttachment attachment = new SupportConversationAttachment();
        attachment.setConversation(conversation);
        attachment.setHotelId(conversation.getHotelId());
        attachment.setUploadedByUserId(actor.getUserId());
        attachment.setOriginalFilename(validated.filename());
        attachment.setContentType(validated.contentType());
        attachment.setSizeBytes((long) validated.bytes().length);
        attachment.setChecksumSha256(validated.checksum());
        attachment.setContentBytes(validated.bytes());
        attachment.setUploadedAt(Instant.now());
        SupportConversationAttachment saved = attachmentRepository.save(attachment);
        auditService.record(
                conversation,
                actor.getUserId(),
                "ATTACHMENT_ADDED",
                "type=" + validated.contentType() + ";size=" + validated.bytes().length
                        + ";sha256=" + validated.checksum());
        return toDto(saved);
    }

    private List<SupportAttachmentDTO> list(Long conversationId) {
        return attachmentRepository.findByConversationIdOrderByUploadedAtAscIdAsc(conversationId)
                .stream().map(this::toDto).toList();
    }

    private SupportConversation requireSupportConversation(
            CustomUserDetails support, Long conversationId, String action) {
        SupportConversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(this::notFound);
        if (authorizationService.isSystemAdministrator(support)) return conversation;
        boolean assigned = userPropertyRepository.findByUserId(support.getUserId()).stream()
                .filter(item -> "ACTIVE".equalsIgnoreCase(item.getStatus()))
                .map(UserProperty::getHotel)
                .anyMatch(hotel -> hotel != null && hotel.getId().equals(conversation.getHotelId()));
        if (!assigned) {
            auditService.recordDenied(
                    conversation, support.getUserId(), "ACCESS_DENIED_" + action,
                    "Actor is outside the conversation tenant");
            throw notFound();
        }
        return conversation;
    }

    private ValidatedAttachment validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalid("ATTACHMENT_EMPTY", HttpStatus.BAD_REQUEST, "Attachment is required.");
        }
        if (file.getSize() > maxBytes) {
            throw invalid("ATTACHMENT_TOO_LARGE", HttpStatus.PAYLOAD_TOO_LARGE,
                    "Attachment exceeds the configured size limit.");
        }
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length == 0) {
                throw invalid("ATTACHMENT_EMPTY", HttpStatus.BAD_REQUEST, "Attachment is required.");
            }
            String detectedType = detectContentType(bytes);
            String declaredType = file.getContentType() == null
                    ? "" : file.getContentType().strip().toLowerCase(Locale.ROOT);
            if (!declaredType.isBlank() && !declaredType.equals(detectedType)
                    && !("image/jpg".equals(declaredType) && "image/jpeg".equals(detectedType))) {
                throw invalid("ATTACHMENT_TYPE_MISMATCH", HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        "Attachment content does not match its declared type.");
            }
            String filename = safeFilename(file.getOriginalFilename(), detectedType);
            return new ValidatedAttachment(filename, detectedType, bytes, sha256(bytes));
        } catch (SupportAttachmentException exception) {
            throw exception;
        } catch (IOException exception) {
            throw invalid("ATTACHMENT_READ_FAILED", HttpStatus.BAD_REQUEST,
                    "Attachment could not be read.");
        }
    }

    private String detectContentType(byte[] bytes) {
        if (startsWith(bytes, "%PDF-")) return "application/pdf";
        if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && startsWith(bytes, 1, "PNG\r\n\u001a\n")) {
            return "image/png";
        }
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            for (byte value : bytes) {
                if (value == 0) throw new CharacterCodingException();
            }
            return "text/plain";
        } catch (CharacterCodingException exception) {
            throw invalid("ATTACHMENT_TYPE_NOT_ALLOWED", HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Only PDF, PNG, JPEG and UTF-8 text attachments are allowed.");
        }
    }

    private String safeFilename(String original, String contentType) {
        String fallback = switch (contentType) {
            case "application/pdf" -> "attachment.pdf";
            case "image/png" -> "attachment.png";
            case "image/jpeg" -> "attachment.jpg";
            default -> "attachment.txt";
        };
        if (original == null || original.isBlank()) return fallback;
        String leaf = original.replace('\\', '/');
        leaf = leaf.substring(leaf.lastIndexOf('/') + 1)
                .replaceAll("[\\p{Cntrl}]", "_")
                .strip();
        if (leaf.isBlank()) return fallback;
        if (leaf.length() > 180) leaf = leaf.substring(leaf.length() - 180);
        String extension = leaf.contains(".")
                ? leaf.substring(leaf.lastIndexOf('.')).toLowerCase(Locale.ROOT) : "";
        boolean matches = switch (contentType) {
            case "application/pdf" -> ".pdf".equals(extension);
            case "image/png" -> ".png".equals(extension);
            case "image/jpeg" -> Set.of(".jpg", ".jpeg").contains(extension);
            default -> ".txt".equals(extension);
        };
        if (!matches) {
            throw invalid("ATTACHMENT_EXTENSION_MISMATCH", HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Attachment filename extension does not match its content.");
        }
        return leaf;
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private boolean startsWith(byte[] bytes, String value) {
        return startsWith(bytes, 0, value);
    }

    private boolean startsWith(byte[] bytes, int offset, String value) {
        if (bytes.length < offset + value.length()) return false;
        for (int index = 0; index < value.length(); index++) {
            if ((bytes[offset + index] & 0xff) != value.charAt(index)) return false;
        }
        return true;
    }

    private SupportAttachmentDTO toDto(SupportConversationAttachment attachment) {
        return new SupportAttachmentDTO(
                attachment.getId(), attachment.getConversation().getId(), attachment.getOriginalFilename(),
                attachment.getContentType(), attachment.getSizeBytes(), attachment.getChecksumSha256(),
                attachment.getUploadedByUserId(), attachment.getUploadedAt());
    }

    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Support attachment was not found.");
    }

    private SupportAttachmentException invalid(String code, HttpStatus status, String message) {
        return new SupportAttachmentException(code, status, message);
    }

    private record ValidatedAttachment(String filename, String contentType, byte[] bytes, String checksum) { }

    public record AttachmentContent(String filename, String contentType, String checksumSha256, byte[] bytes) { }
}
