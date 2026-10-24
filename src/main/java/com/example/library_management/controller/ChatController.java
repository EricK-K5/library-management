package com.example.library_management.controller;


import com.example.library_management.dto.request.ChatRequest;
import com.example.library_management.dto.response.ApiResponse;
import com.example.library_management.dto.response.ChatActionResult;
import com.example.library_management.dto.response.ChatReply;
import com.example.library_management.service.ChatActionService;
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
    ChatActionService chatActionService;

    //    POST
    @PostMapping("/chat")
    ChatReply chat(@RequestBody ChatRequest request){
        return chatService.chat(request);
    }

    //    CHAT WITH IMAGE
    @PostMapping("/chat-with-image")
    ChatReply chat(@RequestPart("file")MultipartFile multipartFile,
                   @RequestPart("message") String message) {
        return chatService.chatWithImage(multipartFile, message);
    }

    // User bấm nút "Xác nhận đặt trước" -> mới thực sự đặt trước.
    // Quyền cần có phụ thuộc loại action (hiện là reservation:create) nên được kiểm tra
    // trong ChatActionService.confirm thay vì @PreAuthorize cố định ở đây.
    @PostMapping("/chat/actions/{actionId}/confirm")
    ApiResponse<ChatActionResult> confirmAction(@PathVariable String actionId) {
        return ApiResponse.<ChatActionResult>builder()
                .result(chatActionService.confirm(actionId))
                .build();
    }

    // User bấm nút "Huỷ"
    @PostMapping("/chat/actions/{actionId}/cancel")
    ApiResponse<Void> cancelAction(@PathVariable String actionId) {
        chatActionService.cancel(actionId);
        return ApiResponse.<Void>builder().build();
    }
}