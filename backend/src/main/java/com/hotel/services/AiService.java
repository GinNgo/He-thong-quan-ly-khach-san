package com.hotel.services;

import com.hotel.dtos.ChatRequest;
import com.hotel.dtos.ChatResponse;
import com.hotel.exceptions.AiServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Service
public class AiService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final String ADMIN_SYSTEM_PROMPT = """
            Bạn là LuxeStay AI Concierge dành cho nhân viên quản trị khách sạn.
            Luôn trả lời bằng tiếng Việt rõ ràng, ngắn gọn, thân thiện; dùng danh sách khi hữu ích.
            Bạn hỗ trợ giải thích quy trình vận hành khách sạn, đặt phòng, phòng, khách hàng,
            dịch vụ, hóa đơn, báo cáo và cách sử dụng hệ thống LuxeStay.
            Không được bịa dữ liệu thời gian thực, mã đặt phòng, doanh thu, phòng trống hoặc trạng thái khách hàng.
            Khi chưa được cung cấp dữ liệu, hãy nói rõ bạn không thể xem dữ liệu đó trong cuộc trò chuyện này
            và hướng dẫn người dùng mở màn hình quản trị phù hợp.
            Không tuyên bố đã tạo, sửa, xóa, duyệt hay thanh toán bất kỳ bản ghi nào.
            Không yêu cầu hoặc hiển thị mật khẩu, token, khóa API hay dữ liệu thẻ thanh toán.
            Nếu yêu cầu có rủi ro hoặc thiếu thông tin, hãy hỏi lại trước khi hướng dẫn.
            """;
    private static final String CUSTOMER_SYSTEM_PROMPT = """
            Bạn là LuxeStay AI Travel Concierge hỗ trợ khách trước khi chuyển sang nhân viên chăm sóc khách hàng.
            Luôn trả lời bằng tiếng Việt rõ ràng, thân thiện và thực tế.
            Bạn có thể tư vấn chọn điểm đến, khu vực lưu trú, loại phòng, tiện nghi, lịch trình và kinh nghiệm du lịch.
            Khi khách hỏi tìm khách sạn hoặc địa điểm, hãy hỏi đủ điểm đến, ngày đi, số khách và ngân sách,
            sau đó hướng dẫn họ dùng mục Tìm kiếm của LuxeStay với các bộ lọc phù hợp.
            Không bịa giá, phòng trống, khoảng cách, đánh giá hoặc dữ liệu thời gian thực.
            Nếu khách cần kiểm tra booking, hoàn tiền, khiếu nại, thay đổi đặt phòng hoặc cần người xử lý,
            hãy đề nghị chọn nút "Gặp nhân viên" trong cửa sổ chat.
            Không yêu cầu mật khẩu, mã OTP, token, khóa API hay thông tin thẻ đầy đủ.
            """;

    private final RestClient restClient;
    private final String apiKey;
    private final List<String> models;
    private final String baseUrl;
    private final AtomicBoolean streamingUnavailable = new AtomicBoolean(false);

    @Autowired
    public AiService(
            RestClient.Builder restClientBuilder,
            @Value("${GEMINI_API_KEY:}") String apiKey,
            @Value("${GEMINI_MODELS:gemini-3.5-flash,gemini-flash-latest,gemini-2.5-flash}") String models,
            @Value("${GEMINI_BASE_URL:https://generativelanguage.googleapis.com/v1beta}") String baseUrl,
            @Value("${GEMINI_STREAMING_ENABLED:true}") boolean streamingEnabled) {
        this.restClient = restClientBuilder.build();
        this.apiKey = apiKey;
        this.models = List.of(models.split(",")).stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.streamingUnavailable.set(!streamingEnabled);
    }

    AiService(RestClient.Builder restClientBuilder, String apiKey, String models, String baseUrl) {
        this(restClientBuilder, apiKey, models, baseUrl, true);
    }

    public ChatResponse processMessage(String username, ChatRequest request) {
        return processMessage(username, request, ADMIN_SYSTEM_PROMPT);
    }

    public ChatResponse processCustomerMessage(String username, ChatRequest request) {
        return processMessage(username, request, CUSTOMER_SYSTEM_PROMPT);
    }

    public void streamCustomerMessage(String username, ChatRequest request, Consumer<String> onChunk) {
        if (!StringUtils.hasText(apiKey)) {
            throw new AiServiceUnavailableException("Gemini chưa được cấu hình. Hãy thiết lập GEMINI_API_KEY cho backend.");
        }
        if (streamingUnavailable.get()) {
            emitGeneratedResponse(username, request, onChunk);
            return;
        }

        List<Map<String, Object>> contents = new ArrayList<>();
        for (ChatRequest.ChatHistoryMessage item : request.getHistory()) {
            String role = "ai".equalsIgnoreCase(item.getRole()) ? "model" : "user";
            contents.add(content(role, item.getText()));
        }
        contents.add(content("user", request.getMessage().trim()));
        Map<String, Object> payload = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of(
                        "text", CUSTOMER_SYSTEM_PROMPT + "\nTên tài khoản hiện tại: " + safeUsername(username)))),
                "contents", contents,
                "generationConfig", Map.of("temperature", 0.25, "maxOutputTokens", 1600)
        );

        RestClientException lastFailure = null;
        int unsupportedModels = 0;
        for (String candidateModel : streamingModels()) {
            AtomicBoolean emitted = new AtomicBoolean(false);
            AtomicBoolean candidateUnsupported = new AtomicBoolean(false);
            try {
                restClient.post()
                        .uri(URI.create(baseUrl + "/models/" + candidateModel + ":streamGenerateContent?alt=sse"))
                        .header("x-goog-api-key", apiKey)
                        .body(payload)
                        .exchange((httpRequest, response) -> {
                            if (!response.getStatusCode().is2xxSuccessful()) {
                                if (response.getStatusCode().value() == 404) {
                                    candidateUnsupported.set(true);
                                    return null;
                                }
                                throw new RestClientException(
                                        "Gemini stream model " + candidateModel + " returned " + response.getStatusCode());
                            }
                            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                                    response.getBody(), StandardCharsets.UTF_8))) {
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    if (!line.startsWith("data:")) continue;
                                    String json = line.substring(5).trim();
                                    if (!StringUtils.hasText(json) || "[DONE]".equals(json)) continue;
                                    String chunk = extractReply(OBJECT_MAPPER.readValue(json, GeminiResponse.class));
                                    if (StringUtils.hasText(chunk)) {
                                        emitted.set(true);
                                        onChunk.accept(chunk);
                                    }
                                }
                            } catch (IOException exception) {
                                throw new RestClientException("Không thể đọc phản hồi streaming từ Gemini", exception);
                            }
                            return null;
                        });
                if (emitted.get()) return;
                if (candidateUnsupported.get()) {
                    unsupportedModels++;
                    continue;
                }
            } catch (RestClientException exception) {
                lastFailure = exception;
                if (emitted.get()) break;
            }
        }
        if (unsupportedModels == models.size()) {
            streamingUnavailable.set(true);
            emitGeneratedResponse(username, request, onChunk);
            return;
        }
        throw new AiServiceUnavailableException(
                "Không thể kết nối streaming Gemini lúc này. Vui lòng thử lại sau.", lastFailure);
    }

    private void emitGeneratedResponse(String username, ChatRequest request, Consumer<String> onChunk) {
        String reply = processCustomerMessage(username, request).getReply();
        for (String chunk : reply.split("(?<=\\s)|(?=\\s)")) {
            if (!chunk.isEmpty()) onChunk.accept(chunk);
        }
    }

    private List<String> streamingModels() {
        List<String> ordered = new ArrayList<>();
        for (String preferred : List.of(
                "gemini-3.5-flash", "gemini-3.6-flash", "gemini-2.5-flash", "gemini-flash-latest")) {
            if (models.contains(preferred)) ordered.add(preferred);
        }
        for (String model : models) {
            if (!ordered.contains(model)) ordered.add(model);
        }
        return ordered;
    }

    private ChatResponse processMessage(String username, ChatRequest request, String systemPrompt) {
        if (!StringUtils.hasText(apiKey)) {
            throw new AiServiceUnavailableException("Gemini chưa được cấu hình. Hãy thiết lập GEMINI_API_KEY cho backend.");
        }

        List<Map<String, Object>> contents = new ArrayList<>();
        for (ChatRequest.ChatHistoryMessage item : request.getHistory()) {
            String role = "ai".equalsIgnoreCase(item.getRole()) ? "model" : "user";
            contents.add(content(role, item.getText()));
        }
        contents.add(content("user", request.getMessage().trim()));

        Map<String, Object> payload = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of(
                        "text", systemPrompt + "\nTên tài khoản hiện tại: " + safeUsername(username)))),
                "contents", contents,
                "generationConfig", Map.of("temperature", 0.3, "maxOutputTokens", 1000)
        );

        RestClientException lastFailure = null;
        for (String candidateModel : models) {
            try {
                GeminiResponse response = restClient.post()
                        .uri(baseUrl + "/models/{model}:generateContent", candidateModel)
                        .header("x-goog-api-key", apiKey)
                        .body(payload)
                        .retrieve()
                        .body(GeminiResponse.class);
                String reply = extractReply(response);
                if (StringUtils.hasText(reply)) {
                    return new ChatResponse(reply.trim());
                }
            } catch (RestClientException exception) {
                lastFailure = exception;
            }
        }
        throw new AiServiceUnavailableException("Không thể kết nối các model Gemini lúc này. Vui lòng thử lại sau.", lastFailure);
    }

    private Map<String, Object> content(String role, String text) {
        return Map.of("role", role, "parts", List.of(Map.of("text", text)));
    }

    private String extractReply(GeminiResponse response) {
        if (response == null || response.candidates() == null) return null;
        return response.candidates().stream()
                .filter(candidate -> candidate.content() != null && candidate.content().parts() != null)
                .flatMap(candidate -> candidate.content().parts().stream())
                .map(GeminiPart::text)
                .filter(StringUtils::hasText)
                .reduce((left, right) -> left + "\n" + right)
                .orElse(null);
    }

    private String safeUsername(String username) {
        if (!StringUtils.hasText(username)) return "Admin";
        String sanitized = username.replaceAll("[^\\p{L}\\p{N}_.@ -]", "");
        return sanitized.substring(0, Math.min(sanitized.length(), 80));
    }

    record GeminiResponse(List<GeminiCandidate> candidates) {}
    record GeminiCandidate(GeminiContent content) {}
    record GeminiContent(List<GeminiPart> parts) {}
    record GeminiPart(String text) {}
}
