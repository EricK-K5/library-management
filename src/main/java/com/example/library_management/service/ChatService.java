package com.example.library_management.service;

import com.example.library_management.dto.request.ChatRequest;
import lombok.experimental.FieldDefaults;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
public class ChatService {
    ChatClient chatClient;
    String systemPrompt;
    JdbcChatMemoryRepository jdbcChatMemoryRepository;

//    Tạo Client nói chuyện với AI
//    Nếu hong tạo thì phải gọi API
//    ChatClient.Builder chỉ là tạo khung, phải .build mới tạo ra ChatClient
    public ChatService(ChatClient.Builder builder,
//    Value cua framework
//    Resource cua spring core
    @Value("classpath:prompts/library-system-prompt.txt") Resource promptResource,
                       JdbcChatMemoryRepository jdbcChatMemoryRepository
                       ) throws IOException {

        this.jdbcChatMemoryRepository = jdbcChatMemoryRepository;

        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(jdbcChatMemoryRepository)
                .maxMessages(30)
                .build();

        this.chatClient = builder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory)
                        .build())
                .defaultOptions(ChatOptions.builder()
                        .temperature(2.0))
                .build();
        this.systemPrompt = new String(
                promptResource.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8
                );
    }

//    Chat with image
    public String chatWithImage(MultipartFile file, String message){
        Media media = Media.builder()
                .mimeType(MimeTypeUtils.parseMimeType(file.getContentType()))
                .data(file.getResource())
                .build();

        return chatClient
                .prompt(systemPrompt)
                .user(
                        promptUserSpec -> promptUserSpec.media(media).text(message))
                .call()
                .content();
    }

    public String chat(ChatRequest request){
        String conversationId = "conversation1";
        SystemMessage systemMessage = new SystemMessage(systemPrompt);
        UserMessage userMessage = new UserMessage(request.message());
        Prompt prompt = new Prompt(systemMessage, userMessage);

        return chatClient
                .prompt(prompt)
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID,conversationId))
                .call()
                .content();
    }

}
