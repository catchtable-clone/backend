package com.catchtable.chatbot.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.catchtable.chatbot.dto.create.ChatMessageRequest;
import com.catchtable.chatbot.dto.create.ChatMessageResponse;
import com.catchtable.chatbot.dto.read.ChatMessageListResponse;
import com.catchtable.chatbot.entity.ChatMessage;
import com.catchtable.chatbot.entity.ChatSession;
import com.catchtable.chatbot.entity.MessageRole;
import com.catchtable.chatbot.repository.ChatMessageRepository;
import com.catchtable.chatbot.repository.ChatSessionRepository;
import com.catchtable.global.exception.CustomException;
import com.catchtable.global.exception.ErrorCode;
import com.catchtable.user.entity.User;
import com.catchtable.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatbotService {

    private static final int DAILY_MESSAGE_LIMIT = 100;

    private static final String SYSTEM_PROMPT =
            "너는 캐치테이블 레스토랑 예약 플랫폼의 AI 도우미야. "
            + "사용자의 예약, 매장 검색, 맛집 추천 요청을 도와줘. "
            + "한국어로 친절하게 답변해.";

    private final ChatClient chatClient;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;

    @Transactional
    public ChatMessageResponse sendMessage(Long userId, ChatMessageRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 세션이 없으면 자동 생성
        ChatSession session = chatSessionRepository.findByUser(user)
                .orElseGet(() -> chatSessionRepository.save(
                        ChatSession.builder().user(user).build()));

        // 일일 메시지 제한 확인
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        long dailyCount = chatMessageRepository.countByChatSessionAndRoleAndCreatedAtAfter(
                session, MessageRole.USER, startOfDay);
        if (dailyCount >= DAILY_MESSAGE_LIMIT) {
            throw new CustomException(ErrorCode.CHAT_DAILY_LIMIT_EXCEEDED);
        }

        // 사용자 메시지 저장
        ChatMessage userMessage = ChatMessage.builder()
                .chatSession(session)
                .role(MessageRole.USER)
                .content(request.message())
                .build();
        chatMessageRepository.save(userMessage);

        // 대화 이력 조회 → Gemini에 전달
        List<ChatMessage> history = chatMessageRepository.findByChatSessionOrderByCreatedAtAsc(session);
        String reply = callAi(history);

        // AI 응답 저장
        ChatMessage assistantMessage = ChatMessage.builder()
                .chatSession(session)
                .role(MessageRole.ASSISTANT)
                .content(reply)
                .build();
        ChatMessage saved = chatMessageRepository.save(assistantMessage);

        return new ChatMessageResponse(saved.getId(), reply);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageListResponse> getMessages(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        ChatSession session = chatSessionRepository.findByUser(user)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_SESSION_NOT_FOUND));

        return chatMessageRepository.findByChatSessionOrderByCreatedAtAsc(session).stream()
                .map(message -> new ChatMessageListResponse(
                        message.getId(),
                        message.getRole(),
                        message.getContent(),
                        message.getCreatedAt()))
                .toList();
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
            throw new CustomException(ErrorCode.CHAT_AI_ERROR);
        }
    }
}
