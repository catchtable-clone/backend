package com.catchtable.chatbot.service;

import java.util.List;

import com.catchtable.chatbot.dto.create.ChatMessageRequest;
import com.catchtable.chatbot.dto.create.ChatMessageResponse;
import com.catchtable.chatbot.dto.read.ChatMessageListResponse;
import com.catchtable.chatbot.entity.ChatMessage;
import com.catchtable.chatbot.entity.MessageRole;
import com.catchtable.global.exception.CustomException;
import com.catchtable.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatbotService {

    private static final int MAX_HISTORY_SIZE = 20;

    private static final String SYSTEM_PROMPT =
            "너는 캐치테이블 레스토랑 예약 플랫폼의 AI 도우미야. "
            + "사용자의 예약, 매장 검색, 맛집 추천 요청을 도와줘. "
            + "한국어로 친절하게 답변해.";

    private final ChatClient chatClient;
    private final ChatbotDbService dbService;

    // 트랜잭션 없음 — 흐름 제어만
    public ChatMessageResponse sendMessage(Long userId, ChatMessageRequest request) {
        // 1단계: DB 작업 (@Transactional) — 세션 조회/생성, 일일 제한 확인, 사용자 메시지 저장
        Long sessionId = dbService.saveUserMessage(userId, request.message());

        // 2단계: AI 호출 (트랜잭션 없음 — DB 커넥션 안 잡음)
        List<ChatMessage> history = dbService.getRecentHistory(sessionId, MAX_HISTORY_SIZE);
        String reply = callAi(history);
        if (reply == null || reply.isBlank()) {
            throw new CustomException(ErrorCode.CHAT_AI_ERROR);
        }

        // 3단계: DB 작업 (@Transactional) — AI 응답 저장
        ChatMessage saved = dbService.saveAssistantMessage(sessionId, reply);

        return new ChatMessageResponse(saved.getId(), reply);
    }

    public List<ChatMessageListResponse> getMessages(Long userId) {
        return dbService.getMessages(userId);
    }

    private String callAi(List<ChatMessage> history) {
        List<Message> messages = new java.util.ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));

        for (ChatMessage msg : history) {
            if (msg.getRole() == MessageRole.USER) {
                messages.add(new UserMessage(msg.getContent()));
            } else {
                messages.add(new AssistantMessage(msg.getContent()));
            }
        }

        try {
            return chatClient.prompt(new Prompt(messages))
                    .call()
                    .content();
        } catch (Exception e) {
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
