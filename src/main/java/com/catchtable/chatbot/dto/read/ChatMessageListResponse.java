package com.catchtable.chatbot.dto.read;

import com.catchtable.chatbot.entity.MessageRole;

import java.time.LocalDateTime;

public record ChatMessageListResponse(
        Long messageId,
        MessageRole role,
        String content,
        LocalDateTime createdAt
) {
}
