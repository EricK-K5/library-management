package com.example.library_management.controller;


import com.example.library_management.dto.request.ChatRequest;
import com.example.library_management.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
public class ChatController {
    ChatService chatService;

    //    POST
    @PostMapping("/chat")
    String chat(@RequestBody ChatRequest request){
        return chatService.chat(request);
    }

//    CHAT WITH IMAGE
    @PostMapping("/chat-with-image")
    String chat(@RequestPart("file")MultipartFile multipartFile,
                @RequestPart("message") String message) {
        return chatService.chatWithImage(multipartFile, message);
    }
}
