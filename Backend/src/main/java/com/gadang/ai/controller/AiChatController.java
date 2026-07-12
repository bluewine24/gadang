package com.gadang.ai.controller;

import com.gadang.ai.ChatAction;
import com.gadang.ai.ChatActionContext;
import com.gadang.common.response.ApiResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 컨시어지 챗봇 진입점 (F1201 controller 계층).
 *
 * <p>현재는 LLM 단순 호출(스모크 테스트) 단계.
 * 이후 단계에서 지역추천·코스생성·공유코스 Tool 연쇄 호출 + Planner 를 연결한다.
 */
@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    private final ChatClient chatClient;

    public AiChatController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public record ChatRequest(String message) {}

    public record ChatReply(String reply, List<ChatAction> actions) {}

    @PostMapping("/chat")
    public ApiResponse<ChatReply> chat(@RequestBody ChatRequest request) {
        ChatActionContext.start();   // 이번 요청의 Tool 액션 수집 시작
        try {
            String reply = chatClient.prompt()
                    .user(request.message())
                    .call()
                    .content();
            return ApiResponse.ok(new ChatReply(reply, ChatActionContext.drain()));
        } finally {
            ChatActionContext.drain();   // 예외 시에도 ThreadLocal 정리
        }
    }
}
