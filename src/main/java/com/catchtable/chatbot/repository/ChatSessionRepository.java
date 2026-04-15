package com.catchtable.chatbot.repository;

import java.util.Optional;

import com.catchtable.chatbot.entity.ChatSession;
import com.catchtable.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    Optional<ChatSession> findByUserAndIsDeletedFalse(User user);
}
