package com.catchtable.chatbot.repository;

import java.time.LocalDateTime;
import java.util.List;

import com.catchtable.chatbot.entity.ChatMessage;
import com.catchtable.chatbot.entity.ChatSession;
import com.catchtable.chatbot.entity.MessageRole;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByChatSessionOrderByCreatedAtAsc(ChatSession chatSession);

    long countByChatSessionAndRoleAndCreatedAtAfter(ChatSession chatSession, MessageRole role, LocalDateTime after);

    List<ChatMessage> findByChatSessionOrderByCreatedAtDesc(ChatSession chatSession, Pageable pageable);
}
