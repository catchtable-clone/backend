package com.catchtable.chatbot.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatbotDbService {

    private static final int DAILY_MESSAGE_LIMIT = 100;

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;

    // 사용자 조회 + 세션 조회/생성 + 일일 제한 확인 + 사용자 메시지 저장
    // sessionId를 직접 반환하여 트랜잭션 종료 후 LazyLoading 문제 방지
    @Transactional
    public Long saveUserMessage(Long userId, String content) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        ChatSession session = chatSessionRepository.findByUserAndIsDeletedFalse(user)
                .orElseGet(() -> chatSessionRepository.save(
                        ChatSession.builder().user(user).build()));

        LocalDateTime startOfDay = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).atStartOfDay();
        long dailyCount = chatMessageRepository.countByChatSessionAndRoleAndCreatedAtAfter(
                session, MessageRole.USER, startOfDay);
        if (dailyCount >= DAILY_MESSAGE_LIMIT) {
            throw new CustomException(ErrorCode.CHAT_DAILY_LIMIT_EXCEEDED);
        }

        ChatMessage userMessage = ChatMessage.builder()
                .chatSession(session)
                .role(MessageRole.USER)
                .content(content)
                .build();

        chatMessageRepository.save(userMessage);
        return session.getId();
    }

    // AI 응답 저장
    @Transactional
    public ChatMessage saveAssistantMessage(Long sessionId, String content) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_SESSION_NOT_FOUND));

        ChatMessage assistantMessage = ChatMessage.builder()
                .chatSession(session)
                .role(MessageRole.ASSISTANT)
                .content(content)
                .build();

        return chatMessageRepository.save(assistantMessage);
    }

    // 대화 이력 조회 (AI 전달용, 최근 20개)
    @Transactional(readOnly = true)
    public List<ChatMessage> getRecentHistory(Long sessionId, int limit) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_SESSION_NOT_FOUND));

        List<ChatMessage> recent = chatMessageRepository.findByChatSessionOrderByCreatedAtDesc(
                session, org.springframework.data.domain.PageRequest.of(0, limit));
        return recent.reversed();
    }

    // 대화 내역 조회 (API 응답용)
    @Transactional(readOnly = true)
    public List<ChatMessageListResponse> getMessages(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        ChatSession session = chatSessionRepository.findByUserAndIsDeletedFalse(user)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_SESSION_NOT_FOUND));

        return chatMessageRepository.findByChatSessionOrderByCreatedAtAsc(session).stream()
                .map(message -> new ChatMessageListResponse(
                        message.getId(),
                        message.getRole(),
                        message.getContent(),
                        message.getCreatedAt()))
                .toList();
    }
}
