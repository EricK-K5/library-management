package com.example.library_management.service;

import com.example.library_management.ai.LibraryTools;
import com.example.library_management.dto.request.ChatRequest;
import com.example.library_management.dto.response.ChatReply;
import com.example.library_management.dto.response.PendingActionResponse;
import com.example.library_management.exception.AppException;
import com.example.library_management.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ChatService {
    ChatClient chatClient;

    public ChatService(ChatClient.Builder builder,
                       @Value("classpath:prompts/library-system-prompt.txt") Resource promptResource,
                       JdbcChatMemoryRepository jdbcChatMemoryRepository,
                       LibraryTools libraryTools) throws IOException {

        String systemPrompt = new String(
                promptResource.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);

        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(jdbcChatMemoryRepository)
                .maxMessages(30)
                .build();

        this.chatClient = builder
                // System prompt đặt ở default -> không phải truyền lại ở mỗi request
                .defaultSystem(systemPrompt)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                // Đăng ký tool: Spring AI quét các method @Tool trong LibraryTools
                .defaultTools(libraryTools)
                // Đã bỏ temperature(2.0): quá cao thì model hay điền sai tham số tool
                .build();
    }

    //    Chat with image
    public ChatReply chatWithImage(MultipartFile file, String message) {
        Media media = Media.builder()
                .mimeType(MimeTypeUtils.parseMimeType(file.getContentType()))
                .data(file.getResource())
                .build();

        String conversationId = currentConversationId();
        // Tool sẽ nhét các "yêu cầu chờ xác nhận" của request này vào list (qua ToolContext, không qua model)
        List<PendingActionResponse> pendingActions = new CopyOnWriteArrayList<>();

        String reply = chatClient
                .prompt()
                .user(promptUserSpec -> promptUserSpec.media(media).text(message))
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .toolContext(Map.<String, Object>of(LibraryTools.PENDING_ACTIONS_KEY, pendingActions))
                .call()
                .content();

        return new ChatReply(reply, List.copyOf(pendingActions));
    }

    public ChatReply chat(ChatRequest request) {
        String conversationId = currentConversationId();
        List<PendingActionResponse> pendingActions = new CopyOnWriteArrayList<>();

        String reply = chatClient
                .prompt()
                .user(request.message())
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .toolContext(Map.<String, Object>of(LibraryTools.PENDING_ACTIONS_KEY, pendingActions))
                .call()
                .content();

        return new ChatReply(reply, List.copyOf(pendingActions));
    }

    // Mỗi user 1 cuộc hội thoại riêng. Đây chỉ để TÁCH lịch sử chat, KHÔNG phải cơ chế phân quyền:
    // quyền vẫn được kiểm tra ở từng tool (LibraryTools.execute) và ở service.
    // Không có authentication hợp lệ thì dừng luôn, không chat bằng conversation chung.
    private String currentConversationId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return "user-" + auth.getName();
    }
}