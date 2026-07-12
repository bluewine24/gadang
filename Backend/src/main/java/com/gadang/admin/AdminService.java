package com.gadang.admin;

import com.gadang.common.exception.GadangException;
import com.gadang.common.response.PageResponse;
import com.gadang.user.User;
import com.gadang.user.UserMapper;
import com.gadang.user.UserSummaryResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {

    private final UserMapper userMapper;
    private final AdminMapper adminMapper;

    public AdminService(UserMapper userMapper, AdminMapper adminMapper) {
        this.userMapper = userMapper;
        this.adminMapper = adminMapper;
    }

    public PageResponse<UserSummaryResponse> users(int page, int size, String query) {
        int safePage = safePage(page);
        int safeSize = safeSize(size);
        String keyword = query == null ? "" : query.trim();
        List<User> pageItems = keyword.isEmpty()
                ? userMapper.findPage(offset(safePage, safeSize), safeSize)
                : userMapper.searchPage(keyword, offset(safePage, safeSize), safeSize);
        long total = keyword.isEmpty() ? userMapper.countAll() : userMapper.countSearch(keyword);
        List<UserSummaryResponse> items = pageItems
                .stream()
                .map(UserSummaryResponse::from)
                .toList();
        return new PageResponse<>(items, safePage, safeSize, total);
    }

    @Transactional
    public UserSummaryResponse updateRole(Long userId, RoleUpdateRequest request) {
        String role = request.role() == null ? "" : request.role().trim().toUpperCase();
        if (!"USER".equals(role) && !"ADMIN".equals(role)) {
            throw GadangException.badRequest("role은 USER 또는 ADMIN만 가능합니다.");
        }
        requireUser(userId);
        userMapper.updateRole(userId, role);
        return UserSummaryResponse.from(requireUser(userId));
    }

    @Transactional
    public void deleteUser(Long userId) {
        requireUser(userId);
        userMapper.deleteById(userId);
    }

    public OperationSummaryResponse operationSummary() {
        return new OperationSummaryResponse(
                userMapper.countAll(),
                adminMapper.countTrips(),
                adminMapper.countPosts(),
                adminMapper.countNotices(),
                adminMapper.countPlaces());
    }

    public PageResponse<AdmissionFee> admissionFees(int page, int size) {
        int safePage = safePage(page);
        int safeSize = safeSize(size);
        return new PageResponse<>(
                adminMapper.findAdmissionFees(offset(safePage, safeSize), safeSize),
                safePage,
                safeSize,
                adminMapper.countAdmissionFees());
    }

    @Transactional
    public AdmissionFee createAdmissionFee(AdmissionFeeRequest request) {
        AdmissionFee fee = toAdmissionFee(new AdmissionFee(), request);
        adminMapper.insertAdmissionFee(fee);
        return adminMapper.findAdmissionFee(fee.getFeeId());
    }

    @Transactional
    public AdmissionFee updateAdmissionFee(Long feeId, AdmissionFeeRequest request) {
        AdmissionFee fee = requireAdmissionFee(feeId);
        adminMapper.updateAdmissionFee(toAdmissionFee(fee, request));
        return adminMapper.findAdmissionFee(feeId);
    }

    @Transactional
    public void deleteAdmissionFee(Long feeId) {
        requireAdmissionFee(feeId);
        adminMapper.deleteAdmissionFee(feeId);
    }

    public PageResponse<BlacklistBrand> blacklist(int page, int size) {
        int safePage = safePage(page);
        int safeSize = safeSize(size);
        return new PageResponse<>(
                adminMapper.findBlacklist(offset(safePage, safeSize), safeSize),
                safePage,
                safeSize,
                adminMapper.countBlacklist());
    }

    @Transactional
    public BlacklistBrand createBlacklistBrand(BlacklistBrandRequest request) {
        BlacklistBrand brand = new BlacklistBrand();
        brand.setBrandName(requireText(request.brandName(), "브랜드명을 입력해 주세요."));
        adminMapper.insertBlacklistBrand(brand);
        return adminMapper.findBlacklistBrand(brand.getId());
    }

    @Transactional
    public BlacklistBrand updateBlacklistBrand(Long id, BlacklistBrandRequest request) {
        BlacklistBrand brand = requireBlacklistBrand(id);
        brand.setBrandName(requireText(request.brandName(), "브랜드명을 입력해 주세요."));
        adminMapper.updateBlacklistBrand(brand);
        return adminMapper.findBlacklistBrand(id);
    }

    @Transactional
    public void deleteBlacklistBrand(Long id) {
        requireBlacklistBrand(id);
        adminMapper.deleteBlacklistBrand(id);
    }

    public PageResponse<PlaceAggregateResponse> placeAggregates(
            int page,
            int size,
            int trimPercent,
            int minSamples,
            Integer minCost,
            Integer maxCost,
            Integer minDuration,
            Integer maxDuration) {
        int safePage = safePage(page);
        int safeSize = safeSize(size);
        int safeTrim = Math.min(Math.max(trimPercent, 0), 40);
        int safeMinSamples = Math.max(minSamples, 1);

        List<PlaceAggregateResponse> rows = adminMapper.findPlaceMetricSamples()
                .stream()
                .collect(Collectors.groupingBy(this::aggregateKey))
                .values()
                .stream()
                .map(samples -> aggregate(samples, safeTrim, minCost, maxCost, minDuration, maxDuration))
                .filter(Objects::nonNull)
                .filter(row -> row.sampleCount() >= safeMinSamples)
                .sorted(Comparator
                        .comparingLong(PlaceAggregateResponse::sampleCount).reversed()
                        .thenComparing(PlaceAggregateResponse::placeName, Comparator.nullsLast(String::compareTo)))
                .toList();

        int from = Math.min(offset(safePage, safeSize), rows.size());
        int to = Math.min(from + safeSize, rows.size());
        return new PageResponse<>(rows.subList(from, to), safePage, safeSize, rows.size());
    }

    private User requireUser(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw GadangException.notFound("회원을 찾을 수 없습니다.");
        }
        return user;
    }

    private AdmissionFee toAdmissionFee(AdmissionFee fee, AdmissionFeeRequest request) {
        if (request.placeId() == null || adminMapper.countPlace(request.placeId()) == 0) {
            throw GadangException.notFound("장소를 찾을 수 없습니다.");
        }
        String feeType = requireText(request.feeType(), "입장료 유형을 입력해 주세요.").toUpperCase();
        if (!List.of("FREE", "FIXED", "ESTIMATED").contains(feeType)) {
            throw GadangException.badRequest("feeType은 FREE, FIXED, ESTIMATED만 가능합니다.");
        }
        fee.setPlaceId(request.placeId());
        fee.setFee(request.fee() == null ? 0 : Math.max(request.fee(), 0));
        fee.setFeeType(feeType);
        return fee;
    }

    private AdmissionFee requireAdmissionFee(Long feeId) {
        AdmissionFee fee = adminMapper.findAdmissionFee(feeId);
        if (fee == null) {
            throw GadangException.notFound("입장료 데이터를 찾을 수 없습니다.");
        }
        return fee;
    }

    private BlacklistBrand requireBlacklistBrand(Long id) {
        BlacklistBrand brand = adminMapper.findBlacklistBrand(id);
        if (brand == null) {
            throw GadangException.notFound("블랙리스트 브랜드를 찾을 수 없습니다.");
        }
        return brand;
    }

    private String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw GadangException.badRequest(message);
        }
        return value.trim();
    }

    private int safePage(int page) {
        return Math.max(page, 1);
    }

    private int safeSize(int size) {
        return Math.min(Math.max(size, 1), 50);
    }

    private int offset(int page, int size) {
        return (page - 1) * size;
    }

    private String aggregateKey(PlaceMetricSample sample) {
        if (sample.placeId() != null) {
            return "id:" + sample.placeId();
        }
        return "name:" + String.valueOf(sample.placeName()).trim().toLowerCase();
    }

    private PlaceAggregateResponse aggregate(
            List<PlaceMetricSample> samples,
            int trimPercent,
            Integer minCost,
            Integer maxCost,
            Integer minDuration,
            Integer maxDuration) {
        if (samples.isEmpty()) {
            return null;
        }
        boolean costFilterActive = minCost != null || maxCost != null;
        boolean durationFilterActive = minDuration != null || maxDuration != null;
        List<PlaceMetricSample> filteredSamples = samples.stream()
                .filter(sample -> !costFilterActive || matchesRange(sample.cost(), minCost, maxCost))
                .filter(sample -> !durationFilterActive || matchesRange(sample.durationMin(), minDuration, maxDuration))
                .toList();
        if (filteredSamples.isEmpty()) {
            return null;
        }

        PlaceMetricSample first = filteredSamples.get(0);
        List<Integer> costs = filteredSamples.stream()
                .map(PlaceMetricSample::cost)
                .filter(value -> value != null && value > 0)
                .toList();
        List<Integer> durations = filteredSamples.stream()
                .map(PlaceMetricSample::durationMin)
                .filter(value -> value != null && value > 0)
                .toList();

        List<Integer> trimmedCosts = trim(costs, trimPercent);
        List<Integer> trimmedDurations = trim(durations, trimPercent);
        long usedSamples = filteredSamples.size();
        if (usedSamples == 0) {
            return null;
        }

        return new PlaceAggregateResponse(
                first.placeId(),
                first.placeName(),
                first.categoryCode(),
                first.categoryName(),
                usedSamples,
                trimmedCosts.size(),
                trimmedDurations.size(),
                average(trimmedCosts),
                average(trimmedDurations),
                min(trimmedCosts),
                max(trimmedCosts),
                min(trimmedDurations),
                max(trimmedDurations));
    }

    private boolean matchesRange(Integer value, Integer min, Integer max) {
        if (value == null || value <= 0) {
            return false;
        }
        return (min == null || value >= min) && (max == null || value <= max);
    }

    private List<Integer> trim(List<Integer> values, int trimPercent) {
        if (values.isEmpty()) {
            return List.of();
        }
        List<Integer> sorted = new ArrayList<>(values);
        sorted.sort(Integer::compareTo);
        int trimCount = sorted.size() * trimPercent / 100;
        if (trimCount * 2 >= sorted.size()) {
            return sorted;
        }
        return sorted.subList(trimCount, sorted.size() - trimCount);
    }

    private int average(List<Integer> values) {
        if (values.isEmpty()) {
            return 0;
        }
        return (int) Math.round(values.stream().mapToInt(Integer::intValue).average().orElse(0));
    }

    private int min(List<Integer> values) {
        return values.stream().mapToInt(Integer::intValue).min().orElse(0);
    }

    private int max(List<Integer> values) {
        return values.stream().mapToInt(Integer::intValue).max().orElse(0);
    }
}
