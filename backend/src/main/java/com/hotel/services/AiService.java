package com.hotel.services;

import com.hotel.dtos.ChatRequest;
import com.hotel.dtos.ChatResponse;
import org.springframework.stereotype.Service;

@Service
public class AiService {

    // In a real application, this would use Spring AI or a RestTemplate to call OpenAI / Gemini APIs.
    // For this mockup, we return predefined logic based on keywords.

    public ChatResponse processMessage(String username, ChatRequest request) {
        String msg = request.getMessage().toLowerCase();
        String reply;

        if (msg.contains("recommend") || msg.contains("suggest") || msg.contains("phòng")) {
            reply = "Chào " + username + " 👋\nDựa trên sở thích của bạn, tôi đề xuất các phòng sau:\n\n" +
                    "- **Phòng Deluxe Hướng Biển**: Không gian rộng rãi, có ban công riêng ngắm biển.\n" +
                    "- **Phòng Suite Cao cấp**: Có bồn tắm sục, dịch vụ quản gia 24/7.\n\n" +
                    "Bạn muốn xem chi tiết hay đặt ngay phòng nào không?";
        } else if (msg.contains("booking") || msg.contains("đặt") || msg.contains("cancel") || msg.contains("hủy")) {
            reply = "Tôi có thể hỗ trợ bạn về việc đặt/hủy phòng. Vui lòng cung cấp Mã Booking (VD: RES-123) hoặc chọn ngày bạn muốn lưu trú nhé.";
        } else if (msg.contains("weather") || msg.contains("thời tiết")) {
            reply = "Thời tiết khu vực khách sạn hiện tại là 28°C, trời nắng đẹp và lý tưởng cho các hoạt động ngoài trời.";
        } else if (msg.contains("xin chào") || msg.contains("hi") || msg.contains("hello")) {
            reply = "Xin chào " + username + " 👋! Tôi là Trợ lý AI của khách sạn. Tôi có thể giúp bạn tìm phòng, gợi ý dịch vụ hoặc hỗ trợ đặt phòng. Bạn cần tôi giúp gì hôm nay?";
        } else {
            reply = "Tôi chưa hiểu rõ ý của bạn. Tôi là Trợ lý AI, bạn có thể hỏi tôi về gợi ý phòng, thời tiết, hoặc các dịch vụ tại khách sạn.";
        }

        return new ChatResponse(reply);
    }
}
