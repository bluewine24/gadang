package com.gadang.ai.config;

import com.gadang.ai.tool.GadangTravelTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

    private static final String SYSTEM_PROMPT = """
            너는 '가당' 여행 서비스의 AI 컨시어지다.
            사용자의 자연어 요청에서 취향과 지역 맥락을 파악해
            제공된 Tool(지역 내 장소 추천·공유 코스 검색)을 호출하고,
            그 실행 결과만을 근거로 한국어로 친절하게 답한다.
            Tool 결과에 없는 장소·비용·시간은 새로 지어내지 않는다.
            지역 추천이나 자동 코스 생성은 기존 화면 기능으로 안내하되, 챗봇 Tool로 직접 실행하지 않는다.
            """;

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, GadangTravelTools travelTools) {
        return builder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultTools(travelTools)
                .build();
    }
}
