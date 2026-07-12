package com.gadang.admin;

import com.gadang.common.response.ApiResponse;
import com.gadang.common.response.PageResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/operation-data")
public class AdminOperationController {

    private final AdminService adminService;

    public AdminOperationController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/summary")
    public ApiResponse<OperationSummaryResponse> summary() {
        return ApiResponse.ok(adminService.operationSummary());
    }

    @GetMapping("/admission-fees")
    public ApiResponse<PageResponse<AdmissionFee>> admissionFees(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(adminService.admissionFees(page, size));
    }

    @GetMapping("/place-aggregates")
    public ApiResponse<PageResponse<PlaceAggregateResponse>> placeAggregates(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "10") int trimPercent,
            @RequestParam(defaultValue = "1") int minSamples,
            @RequestParam(required = false) Integer minCost,
            @RequestParam(required = false) Integer maxCost,
            @RequestParam(required = false) Integer minDuration,
            @RequestParam(required = false) Integer maxDuration) {
        return ApiResponse.ok(adminService.placeAggregates(
                page, size, trimPercent, minSamples, minCost, maxCost, minDuration, maxDuration));
    }

    @PostMapping("/admission-fees")
    public ApiResponse<AdmissionFee> createAdmissionFee(@RequestBody AdmissionFeeRequest request) {
        return ApiResponse.ok("입장료 데이터가 등록되었습니다.", adminService.createAdmissionFee(request));
    }

    @PatchMapping("/admission-fees/{feeId}")
    public ApiResponse<AdmissionFee> updateAdmissionFee(@PathVariable Long feeId, @RequestBody AdmissionFeeRequest request) {
        return ApiResponse.ok("입장료 데이터가 수정되었습니다.", adminService.updateAdmissionFee(feeId, request));
    }

    @DeleteMapping("/admission-fees/{feeId}")
    public ApiResponse<Void> deleteAdmissionFee(@PathVariable Long feeId) {
        adminService.deleteAdmissionFee(feeId);
        return ApiResponse.ok("입장료 데이터가 삭제되었습니다.", null);
    }

    @GetMapping("/franchise-blacklist")
    public ApiResponse<PageResponse<BlacklistBrand>> blacklist(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(adminService.blacklist(page, size));
    }

    @PostMapping("/franchise-blacklist")
    public ApiResponse<BlacklistBrand> createBlacklistBrand(@RequestBody BlacklistBrandRequest request) {
        return ApiResponse.ok("프랜차이즈 블랙리스트가 등록되었습니다.", adminService.createBlacklistBrand(request));
    }

    @PatchMapping("/franchise-blacklist/{id}")
    public ApiResponse<BlacklistBrand> updateBlacklistBrand(@PathVariable Long id, @RequestBody BlacklistBrandRequest request) {
        return ApiResponse.ok("프랜차이즈 블랙리스트가 수정되었습니다.", adminService.updateBlacklistBrand(id, request));
    }

    @DeleteMapping("/franchise-blacklist/{id}")
    public ApiResponse<Void> deleteBlacklistBrand(@PathVariable Long id) {
        adminService.deleteBlacklistBrand(id);
        return ApiResponse.ok("프랜차이즈 블랙리스트가 삭제되었습니다.", null);
    }
}
