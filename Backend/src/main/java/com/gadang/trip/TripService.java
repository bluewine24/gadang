package com.gadang.trip;

import com.gadang.common.exception.GadangException;
import com.gadang.course.dto.CourseResponse;
import com.gadang.mypage.TripSummaryResponse;
import com.gadang.trip.TripDtos.TripDetailResponse;
import com.gadang.trip.TripDtos.TripSaveRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TripService {

    private final TripMapper tripMapper;
    private final ObjectMapper objectMapper;

    public TripService(TripMapper tripMapper, ObjectMapper objectMapper) {
        this.tripMapper = tripMapper;
        this.objectMapper = objectMapper;
    }

    /** 추천 코스를 확정 일정으로 저장 → tripId 반환. */
    @Transactional
    public Long save(Long userId, TripSaveRequest req) {
        if (req == null || req.course() == null) {
            throw GadangException.badRequest("저장할 코스 정보가 필요합니다.");
        }
        CourseResponse c = req.course();
        List<CourseResponse.CourseItem> timeline = c.items() == null ? List.of() : c.items();
        if (timeline.stream().noneMatch(this::isPlaceItem)) {
            throw GadangException.badRequest("저장할 방문 장소가 필요합니다.");
        }

        TripPlan plan = new TripPlan();
        plan.setUserId(userId);
        plan.setRegionId(null); // 카카오 출처 지역이라 매핑 생략(NULL 허용)
        plan.setTitle(req.title() != null && !req.title().isBlank()
                ? req.title().trim()
                : (nz(c.region()) + " 당일치기"));
        plan.setTripDate(req.tripDate() != null ? req.tripDate() : LocalDate.now());
        plan.setStartPoint(nz(c.startPoint(), "-"));
        plan.setEndPoint(c.startPoint());
        plan.setDepartureTime(parseTime(c.startTime(), LocalTime.of(9, 0)));
        plan.setReturnTime(parseTime(c.endTime(), LocalTime.of(21, 0)));
        plan.setTotalCost(c.totalCost());
        plan.setFoodCostEst(foodCost(c));
        plan.setCourseJson(objectMapper.writeValueAsString(c));

        tripMapper.insert(plan);
        Map<Integer, Long> timelinePlaceItems = insertTripItems(plan.getTripId(), timeline);
        insertTripRoutes(plan.getTripId(), timeline, timelinePlaceItems);
        return plan.getTripId();
    }

    /** 내 확정 일정 목록. */
    public List<TripSummaryResponse> list(Long userId) {
        return tripMapper.findByUser(userId);
    }

    /** 확정 일정 상세 (코스 타임라인 포함). */
    public TripDetailResponse get(Long userId, Long tripId) {
        TripPlan plan = tripMapper.findById(tripId);
        if (plan == null) {
            throw GadangException.notFound("일정을 찾을 수 없습니다.");
        }
        if (!plan.getUserId().equals(userId)) {
            throw GadangException.forbidden();
        }
        CourseResponse course = null;
        if (plan.getCourseJson() != null && !plan.getCourseJson().isBlank()) {
            course = objectMapper.readValue(plan.getCourseJson(), CourseResponse.class);
        }
        return new TripDetailResponse(
                plan.getTripId(), plan.getTitle(), plan.getTripDate(),
                plan.getStartPoint(), plan.getEndPoint(),
                plan.getDepartureTime(), plan.getReturnTime(),
                plan.getBudgetGuide(), plan.getTotalCost(), plan.getFoodCostEst(),
                course);
    }

    /** 확정 일정 삭제. */
    @Transactional
    public void delete(Long userId, Long tripId) {
        if (tripMapper.delete(tripId, userId) == 0) {
            throw GadangException.notFound("삭제할 일정을 찾을 수 없습니다.");
        }
    }

    // ── helpers ──────────────────────────────────────────────

    private int foodCost(CourseResponse c) {
        if (c.items() == null) return 0;
        return c.items().stream()
                .filter(this::isPlaceItem)
                .filter(it -> "food".equals(it.cat()) || "cafe".equals(it.cat()))
                .mapToInt(it -> it.fee() == null ? 0 : it.fee())
                .sum();
    }

    private Map<Integer, Long> insertTripItems(Long tripId, List<CourseResponse.CourseItem> timeline) {
        Map<Integer, Long> timelinePlaceItems = new HashMap<>();
        int fallbackOrder = 1;
        for (int i = 0; i < timeline.size(); i++) {
            CourseResponse.CourseItem item = timeline.get(i);
            if (!isPlaceItem(item)) continue;

            Long placeId = findOrCreatePlace(item);
            int visitOrder = item.order() != null ? item.order() : fallbackOrder;
            fallbackOrder = Math.max(fallbackOrder, visitOrder + 1);

            TripItemRow row = new TripItemRow();
            row.setTripId(tripId);
            row.setPlaceId(placeId);
            row.setVisitOrder(visitOrder);
            row.setArrivalTime(parseTime(item.arr(), null));
            row.setStayMinutes(item.stay() != null ? item.stay() : 60);
            row.setAdmissionFee(isFoodOrCafe(item) ? 0 : value(item.fee()));
            row.setFoodCost(isFoodOrCafe(item) ? value(item.fee()) : 0);
            tripMapper.insertItem(row);
            timelinePlaceItems.put(i, row.getItemId());
        }
        return timelinePlaceItems;
    }

    private void insertTripRoutes(Long tripId,
                                  List<CourseResponse.CourseItem> timeline,
                                  Map<Integer, Long> timelinePlaceItems) {
        for (int i = 0; i < timeline.size(); i++) {
            CourseResponse.CourseItem item = timeline.get(i);
            if (!"transit".equals(item.type())) continue;

            Long fromItemId = nearestPlaceItemBefore(i, timelinePlaceItems);
            Long toItemId = nearestPlaceItemAfter(i, timeline.size(), timelinePlaceItems);
            if (toItemId == null) {
                toItemId = fromItemId;
            }
            if (toItemId == null) {
                continue;
            }

            TripRouteRow row = new TripRouteRow();
            row.setTripId(tripId);
            row.setFromItemId(fromItemId);
            row.setToItemId(toItemId);
            row.setTransportType(transportType(item.mode()));
            row.setDurationMinutes(value(item.min()));
            row.setFare(value(item.fare()));
            tripMapper.insertRoute(row);
        }
    }

    private Long nearestPlaceItemBefore(int index, Map<Integer, Long> timelinePlaceItems) {
        for (int i = index - 1; i >= 0; i--) {
            Long itemId = timelinePlaceItems.get(i);
            if (itemId != null) return itemId;
        }
        return null;
    }

    private Long nearestPlaceItemAfter(int index, int size, Map<Integer, Long> timelinePlaceItems) {
        for (int i = index + 1; i < size; i++) {
            Long itemId = timelinePlaceItems.get(i);
            if (itemId != null) return itemId;
        }
        return null;
    }

    private Long findOrCreatePlace(CourseResponse.CourseItem item) {
        String kakaoPlaceId = placeKey(item);
        Long existing = tripMapper.findPlaceIdByKakaoPlaceId(kakaoPlaceId);
        if (existing != null) {
            return existing;
        }

        TripPlace place = new TripPlace();
        place.setKakaoPlaceId(kakaoPlaceId);
        place.setName(nz(item.name(), "이름 없는 장소"));
        place.setCategoryCode(categoryCode(item.cat()));
        place.setCategoryName(nz(item.cat(), "course"));
        place.setAddress(truncate(nz(item.note(), ""), 300));
        place.setLat(item.lat() != null ? item.lat() : 0);
        place.setLng(item.lng() != null ? item.lng() : 0);
        tripMapper.insertPlace(place);
        return place.getPlaceId();
    }

    private String placeKey(CourseResponse.CourseItem item) {
        if (item.pid() != null && !item.pid().isBlank()) {
            return item.pid();
        }
        return "COURSE:" + nz(item.name(), "unknown").replaceAll("\\s+", "")
                + ":" + (item.lat() != null ? item.lat() : 0)
                + ":" + (item.lng() != null ? item.lng() : 0);
    }

    private boolean isPlaceItem(CourseResponse.CourseItem item) {
        return item != null && "place".equals(item.type());
    }

    private boolean isFoodOrCafe(CourseResponse.CourseItem item) {
        return "food".equals(item.cat()) || "cafe".equals(item.cat()) || item.meal() != null;
    }

    private String categoryCode(String cat) {
        return switch (nz(cat)) {
            case "food" -> "FD6";
            case "cafe" -> "CE7";
            case "culture" -> "CT1";
            case "shop" -> "MT1";
            default -> "AT4";
        };
    }

    private String transportType(String mode) {
        String m = nz(mode).toUpperCase();
        if (m.contains("도보") || m.contains("WALK")) return "WALK";
        if (m.contains("지하철") || m.contains("SUBWAY")) return "SUBWAY";
        if (m.contains("KTX")) return "KTX";
        if (m.contains("버스") || m.contains("BUS")) return "BUS";
        return "ESTIMATED";
    }

    private LocalTime parseTime(String hhmm, LocalTime fallback) {
        if (hhmm == null) return fallback;
        try {
            String[] p = hhmm.trim().split(":");
            return LocalTime.of(Integer.parseInt(p[0]), Integer.parseInt(p[1]));
        } catch (Exception e) {
            return fallback;
        }
    }

    private static int value(Integer value) {
        return value != null ? value : 0;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength);
    }

    private String nz(String s) { return s == null ? "" : s; }
    private String nz(String s, String def) { return s == null || s.isBlank() ? def : s; }
}
