package com.gadang.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    /** 외부 API 공통 타임아웃 — 무한 대기 방지 (연결 3초 / 응답 10초) */
    private static SimpleClientHttpRequestFactory timeoutFactory() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(3_000);
        f.setReadTimeout(10_000);
        return f;
    }

    @Value("${kakao.api.key}")
    private String kakaoApiKey;

    @Value("${odsay.api.key}")
    private String odsayApiKey;

    @Bean("kakaoRestClient")
    public RestClient kakaoRestClient() {
        return RestClient.builder().requestFactory(timeoutFactory())
                .baseUrl("https://dapi.kakao.com")
                .defaultHeader("Authorization", "KakaoAK " + kakaoApiKey)
                // 서버-서버 호출 시 KA 헤더 필요 (Kakao 보안 정책)
                // KA 형식: sdk/{version} os/{platform} origin/{domain}
                .defaultHeader("KA", "sdk/1.0.0 os/java origin/https://gadang.local")
                .defaultHeader("Referer", "https://gadang.local")
                .build();
    }

    @Bean("odsayRestClient")
    public RestClient odsayRestClient() {
        return RestClient.builder().requestFactory(timeoutFactory())
                .baseUrl("https://api.odsay.com/v1/api")
                .build();
    }

    @Bean("naverRestClient")
    public RestClient naverRestClient(
            @Value("${naver.client-id}") String clientId,
            @Value("${naver.client-secret}") String clientSecret) {
        return RestClient.builder().requestFactory(timeoutFactory())
                .baseUrl("https://openapi.naver.com/v1")
                .defaultHeader("X-Naver-Client-Id", clientId)
                .defaultHeader("X-Naver-Client-Secret", clientSecret)
                .build();
    }

    @Bean("tourRestClient")
    public RestClient tourRestClient() {
        return RestClient.builder().requestFactory(timeoutFactory())
                .baseUrl("https://apis.data.go.kr/B551011/KorService2")
                .build();
    }

    @Bean("openMeteoRestClient")
    public RestClient openMeteoRestClient() {
        return RestClient.builder().requestFactory(timeoutFactory())
                .baseUrl("https://api.open-meteo.com/v1")
                .build();
    }

    /** 가당 RAG 서버(FastAPI) — 공유 코스 의미 검색 (AI Server 분리) */
    @Bean("ragRestClient")
    public RestClient ragRestClient(@Value("${gadang.rag.base-url:http://localhost:8000}") String baseUrl) {
        return RestClient.builder().requestFactory(timeoutFactory())
                .baseUrl(baseUrl)
                .build();
    }

    @Bean("korailTrainRestClient")
    public RestClient korailTrainRestClient() {
        return RestClient.builder().requestFactory(timeoutFactory())
                .baseUrl("https://apis.data.go.kr/1613000/TrainInfoService")
                .build();
    }
}
