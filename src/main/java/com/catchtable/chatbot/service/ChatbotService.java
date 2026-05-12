package com.catchtable.chatbot.service;

import com.catchtable.chatbot.dto.create.ChatMessageRequest;
import com.catchtable.chatbot.dto.create.ChatMessageResponse;
import com.catchtable.chatbot.dto.read.ChatMessageListResponse;
import com.catchtable.chatbot.entity.ChatMessage;
import com.catchtable.chatbot.entity.MessageRole;
import com.catchtable.coupon.service.CouponService;
import com.catchtable.global.exception.CustomException;
import com.catchtable.global.exception.ErrorCode;
import com.catchtable.reservation.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatbotService {

    private static final int MAX_HISTORY_SIZE = 20;

    private String buildSystemPrompt() {
            return "너는 'CatchEat(캐치잇)'이라는 레스토랑 예약 플랫폼의 AI 비서야. "
                    + "오늘 날짜는 " + java.time.LocalDate.now() + "이야. "
                    + "사용자가 '5월 12일'처럼 연도 없이 날짜를 말하면 오늘 날짜 기준으로 가장 가까운 미래 날짜로 해석해. "
                    + "너의 역할은 사용자의 질문을 이해하고, 주어진 도구(함수)를 사용하여 레스토랑 예약 요청을 처리하는 것이야. "
                    + "사용자가 예약을 요청하면, 'createReservationFromAi' 함수를 호출하기 전에 반드시 'getAvailableCouponsForAi' 함수를 먼저 호출해서 사용자에게 사용 가능한 쿠폰이 있는지 확인하고, 있다면 어떤 쿠폰을 사용할지 물어봐야 해."
                    + "만약 사용 가능한 쿠폰이 없다면, 바로 'createReservationFromAi' 함수를 호출해서 예약을 진행해. "
                    + "사용자가 쿠폰을 사용하겠다고 하면, 'createReservationFromAi' 함수를 호출할 때 'couponId' 파라미터를 포함해서 호출해야 해."
                    + "함수를 호출하기 전에 '매장 이름', '날짜', '시간', '인원수' 4가지 정보가 모두 있는지 확인해. "
                    + "정보가 부족하면 사용자에게 추가 정보를 요청해. "
                    + "모든 답변은 한국어로, 친절하고 명확하게 제공해야 해.";
    }

    private final ChatClient chatClient;
    private final ChatbotDbService dbService;
    private final ReservationService reservationService;
    private final CouponService couponService;

    public ChatMessageResponse sendMessage(Long userId, ChatMessageRequest request) {
        Long sessionId = dbService.saveUserMessage(userId, request.message());
        List<ChatMessage> history = dbService.getRecentHistory(sessionId, MAX_HISTORY_SIZE);

        String reply = callAi(history, userId);

        if (reply == null || reply.isBlank()) {
            throw new CustomException(ErrorCode.CHAT_AI_ERROR);
        }

        ChatMessage saved = dbService.saveAssistantMessage(sessionId, reply);
        return new ChatMessageResponse(saved.getId(), reply);
    }

    public List<ChatMessageListResponse> getMessages(Long userId) {
        return dbService.getMessages(userId);
    }

    private String callAi(List<ChatMessage> history, Long userId) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(buildSystemPrompt()));

        for (ChatMessage msg : history) {
            if (msg.getRole() == MessageRole.USER) {
                messages.add(new UserMessage(msg.getContent()));
            } else {
                messages.add(new AssistantMessage(msg.getContent()));
            }
        }

        try {
            return chatClient.prompt()
                    .messages(messages)
                    .tools(reservationService, couponService)
                    .toolContext(Map.of("userId", userId))
                    .call()
                    .content();

        } catch (Exception e) {
            handleAiException(e);
            return null;
        }
    }

    private void handleAiException(Exception e) {
        log.error("AI API 호출 중 예외 발생", e);

        String message = getFullErrorMessage(e).toLowerCase();
        if (message.contains("api key") || message.contains("auth") || message.contains("401")) {
            throw new CustomException(ErrorCode.CHAT_AI_AUTH_ERROR);
        }
        if (message.contains("rate") || message.contains("429") || message.contains("quota")) {
            throw new CustomException(ErrorCode.CHAT_AI_RATE_LIMIT);
        }
        if (message.contains("timeout") || message.contains("timed out")) {
            throw new CustomException(ErrorCode.CHAT_AI_TIMEOUT);
        }
        throw new CustomException(ErrorCode.CHAT_AI_ERROR);
    }

    private String getFullErrorMessage(Exception e) {
        StringBuilder sb = new StringBuilder();
        Throwable current = e;
        while (current != null) {
            if (current.getMessage() != null) {
                sb.append(current.getMessage()).append(" ");
            }
            current = current.getCause();
        }
        return sb.toString();
    }
}
