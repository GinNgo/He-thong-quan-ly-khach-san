package com.hotel.services;

import com.hotel.dtos.ChatRequest;
import com.hotel.exceptions.AiServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class AiServiceTest {
    @Test
    void sendsConversationToGeminiAndReturnsModelText() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AiService service = new AiService(builder, "test-key", "gemini-test", "https://gemini.test/v1beta");
        ChatRequest request = new ChatRequest("Tóm tắt quy trình check-in");
        request.setHistory(List.of(new ChatRequest.ChatHistoryMessage("user", "Tôi là lễ tân")));

        server.expect(requestTo("https://gemini.test/v1beta/models/gemini-test:generateContent"))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andExpect(jsonPath("$.contents[0].role").value("user"))
                .andExpect(jsonPath("$.contents[1].parts[0].text").value("Tóm tắt quy trình check-in"))
                .andRespond(withSuccess("""
                        {"candidates":[{"content":{"parts":[{"text":"1. Xác minh đặt phòng"}]}}]}
                        """, MediaType.APPLICATION_JSON));

        assertEquals("1. Xác minh đặt phòng", service.processMessage("admin", request).getReply());
        server.verify();
    }

    @Test
    void failsClosedWhenApiKeyIsMissing() {
        AiService service = new AiService(RestClient.builder(), "", "gemini-test", "https://gemini.test/v1beta");

        AiServiceUnavailableException exception = assertThrows(
                AiServiceUnavailableException.class,
                () -> service.processMessage("admin", new ChatRequest("Xin chào")));

        assertEquals("Gemini chưa được cấu hình. Hãy thiết lập GEMINI_API_KEY cho backend.", exception.getMessage());
    }

    @Test
    void fallsBackToTheNextConfiguredModel() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AiService service = new AiService(
                builder, "test-key", "missing-model,working-model", "https://gemini.test/v1beta");

        server.expect(requestTo("https://gemini.test/v1beta/models/missing-model:generateContent"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo("https://gemini.test/v1beta/models/working-model:generateContent"))
                .andRespond(withSuccess("""
                        {"candidates":[{"content":{"parts":[{"text":"Model dự phòng hoạt động"}]}}]}
                        """, MediaType.APPLICATION_JSON));

        assertEquals(
                "Model dự phòng hoạt động",
                service.processCustomerMessage("customer", new ChatRequest("Tìm nơi lưu trú")).getReply());
        server.verify();
    }

    @Test
    void streamsGeminiChunksAsTheyArrive() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AiService service = new AiService(builder, "test-key", "gemini-test", "https://gemini.test/v1beta");
        List<String> chunks = new ArrayList<>();

        server.expect(requestTo("https://gemini.test/v1beta/models/gemini-test:streamGenerateContent?alt=sse"))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andRespond(withSuccess("""
                        data: {"candidates":[{"content":{"parts":[{"text":"Xin chào "}],"role":"model"}}]}

                        data: {"candidates":[{"content":{"parts":[{"text":"bạn"}]}}]}

                        """, MediaType.TEXT_EVENT_STREAM));

        service.streamCustomerMessage("customer", new ChatRequest("Tìm khách sạn"), chunks::add);

        assertEquals(List.of("Xin chào ", "bạn"), chunks);
        server.verify();
    }

    @Test
    void fallsBackWithinTheSameSseRequestWhenStreamingIsUnsupported() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AiService service = new AiService(builder, "test-key", "gemini-test", "https://gemini.test/v1beta");
        List<String> chunks = new ArrayList<>();

        server.expect(requestTo("https://gemini.test/v1beta/models/gemini-test:streamGenerateContent?alt=sse"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo("https://gemini.test/v1beta/models/gemini-test:generateContent"))
                .andRespond(withSuccess("""
                        {"candidates":[{"content":{"parts":[{"text":"Phản hồi dự phòng"}]}}]}
                        """, MediaType.APPLICATION_JSON));

        service.streamCustomerMessage("customer", new ChatRequest("Xin chào"), chunks::add);

        assertEquals("Phản hồi dự phòng", String.join("", chunks));
        server.verify();
    }

    @Test
    void triesTheNextModelWhenOnlyOneModelDoesNotSupportStreaming() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AiService service = new AiService(
                builder, "test-key", "missing-stream,working-stream", "https://gemini.test/v1beta");
        List<String> chunks = new ArrayList<>();

        server.expect(requestTo("https://gemini.test/v1beta/models/missing-stream:streamGenerateContent?alt=sse"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo("https://gemini.test/v1beta/models/working-stream:streamGenerateContent?alt=sse"))
                .andRespond(withSuccess("""
                        data: {"candidates":[{"content":{"parts":[{"text":"Streaming hoạt động"}]}}]}

                        """, MediaType.TEXT_EVENT_STREAM));

        service.streamCustomerMessage("customer", new ChatRequest("Xin chào"), chunks::add);

        assertEquals(List.of("Streaming hoạt động"), chunks);
        server.verify();
    }
}
