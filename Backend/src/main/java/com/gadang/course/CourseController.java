package com.gadang.course;

import com.gadang.common.response.ApiResponse;
import com.gadang.course.dto.CourseRequest;
import com.gadang.course.dto.CourseRegenerateRequest;
import com.gadang.course.dto.CourseResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/course")
@RequiredArgsConstructor
public class CourseController {

    private final CourseRecommendationService courseRecommendationService;

    @PostMapping("/generate")
    public ApiResponse<CourseResponse> generate(@RequestBody CourseRequest request) {
        return ApiResponse.ok(courseRecommendationService.generate(request));
    }

    @PostMapping("/regenerate")
    public ApiResponse<CourseResponse> regenerate(@RequestBody CourseRegenerateRequest request) {
        return ApiResponse.ok(courseRecommendationService.regenerate(request));
    }
}
