package com.example.library_management.service;

import com.example.library_management.dto.request.ChatRequest;
import lombok.experimental.FieldDefaults;
import org.springframework.ai.chat.client.ChatClient;
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
import org.springframework.ai.chat.prompt.ChatOptions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
public class ChatService {
    ChatClient chatClient;
    String systemPrompt;

//    Tạo Client nói chuyện với AI
//    Nếu hong tạo thì phải gọi API
//    ChatClient.Builder chỉ là tạo khung, phải .build mới tạo ra ChatClient
    public ChatService(ChatClient.Builder builder,
//    Value cua framework
//    Resource cua spring core
    @Value("classpath:prompts/library-system-prompt.txt") Resource promptResource) throws IOException {

        this.chatClient = builder
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
        SystemMessage systemMessage = new SystemMessage(systemPrompt);
        UserMessage userMessage = new UserMessage(request.message());
        Prompt prompt = new Prompt(systemMessage, userMessage);

        return chatClient
                .prompt(prompt)
                .call()
                .content();
    }

}
