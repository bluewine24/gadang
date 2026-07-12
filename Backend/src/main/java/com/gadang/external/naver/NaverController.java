package com.gadang.external.naver;

import com.gadang.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/trend")
@RequiredArgsConstructor
public class NaverController {

    private final NaverBlogService naverBlogService;

    /**
     * GET /api/trend/blog?place=해운대
     * → { count: 4658152, score: 88.3, worthVisiting: true }
     */
    @GetMapping("/blog")
    public ResponseEntity<ApiResponse<Map<String, Object>>> blogTrend(
            @RequestParam String place) {

        int count = naverBlogService.getBlogCount(place);
        double score = naverBlogService.calcTrendScore(place);
        boolean worth = naverBlogService.isWorthVisiting(place);

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "place", place,
                "count", count,
                "score", Math.round(score * 10) / 10.0,
                "worthVisiting", worth
        )));
    }
}
