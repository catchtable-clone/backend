package com.catchtable.chatbot.controller;

import java.util.List;

import com.catchtable.chatbot.dto.create.ChatMessageRequest;
import com.catchtable.chatbot.dto.create.ChatMessageResponse;
import com.catchtable.chatbot.dto.read.ChatMessageListResponse;
import com.catchtable.chatbot.service.ChatbotService;
import com.catchtable.global.common.ApiResponse;
import com.catchtable.global.common.SuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatbotController {

    private final ChatbotService chatbotService;

    @PostMapping("/messages")
    public ResponseEntity<ApiResponse<ChatMessageResponse>> sendMessage(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody ChatMessageRequest request
    ) {
        ChatMessageResponse responseData = chatbotService.sendMessage(userId, request);
        return ResponseEntity
                .status(SuccessCode.CHAT_MESSAGE_SENT.getHttpStatus())
                .body(ApiResponse.success(SuccessCode.CHAT_MESSAGE_SENT, responseData));
    }

    @GetMapping("/messages")
    public ResponseEntity<ApiResponse<List<ChatMessageListResponse>>> getMessages(
            @RequestHeader("X-User-Id") Long userId
    ) {
        List<ChatMessageListResponse> responseData = chatbotService.getMessages(userId);
        return ResponseEntity
                .status(SuccessCode.CHAT_MESSAGE_LIST_OK.getHttpStatus())
                .body(ApiResponse.success(SuccessCode.CHAT_MESSAGE_LIST_OK, responseData));
    }
}
