package com.gadang.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {
    // 캐시 설정은 application.properties의 spring.cache.caffeine.spec 으로 관리
    // 캐시 이름 상수 정의
    public static final String KAKAO_PLACE_CACHE = "kakaoPlace";    // 장소 검색 결과
    public static final String ODSAY_ROUTE_CACHE = "odsayRoute";    // 교통 경로
    public static final String TREND_SCORE_CACHE = "trendScore";    // 트렌드 점수
}
