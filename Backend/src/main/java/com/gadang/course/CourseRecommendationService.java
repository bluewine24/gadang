package com.gadang.course;

import com.gadang.algorithm.PlaceCandidate;
import com.gadang.algorithm.CourseCandidateProvider;
import com.gadang.algorithm.RegionSeedData;
import com.gadang.algorithm.ScoredPlaceProvider;
import com.gadang.common.exception.GadangException;
import com.gadang.course.dto.CourseRequest;
import com.gadang.course.dto.CourseRequest.ActivityType;
import com.gadang.course.dto.CourseRequest.MealConfig;
import com.gadang.course.dto.CourseRegenerateRequest;
import com.gadang.course.dto.CourseRegenerateRequest.EditEntry;
import com.gadang.course.dto.CourseRegenerateRequest.EntryType;
import com.gadang.course.dto.CourseResponse;
import com.gadang.external.odsay.OdsayTransitService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class CourseRecommendationService {

    private static final int TOP_K = 16;
    private static final int MAX_PLACES = 8;
    private static final int MAX_ANCHORS = 6;
    private static final int MIN_CANDIDATE_RADIUS_METERS = 15_000;
    private static final int MAX_CANDIDATE_RADIUS_METERS = 20_000;
    private static final int DEFAULT_CAFE_COST = 7_000;
    private static final int DEFAULT_LUNCH_COST = 15_000;
    private static final int DEFAULT_DINNER_COST = 20_000;
    private static final int MEAL_PRICE_STEP = 5_000;
    private static final LocalTime DEFAULT_DEPARTURE = LocalTime.of(10, 0);
    private static final LocalTime DEFAULT_RETURN = LocalTime.of(19, 0);
    private static final LocalTime LUNCH_START = LocalTime.of(11, 0);
    private static final LocalTime LUNCH_END = LocalTime.of(14, 0);
    private static final LocalTime CAFE_START = LocalTime.of(14, 0);
    private static final LocalTime CAFE_END = LocalTime.of(17, 0);
    private static final LocalTime DINNER_START = LocalTime.of(17, 0);
    private static final LocalTime DINNER_END = LocalTime.of(20, 0);
    private static final double RANKING_RANDOM_SPREAD = 6.0;
    private static final double ROUTE_RANDOM_SPREAD = 8.0;

    private final ScoredPlaceProvider scoredPlaceProvider;
    private final OdsayTransitService odsayTransitService;

    public CourseResponse generate(CourseRequest request) {
        if (request == null) {
            throw GadangException.badRequest("요청 본문이 필요합니다.");
        }
        if (request.getPreferenceEntries() != null && !request.getPreferenceEntries().isEmpty()) {
            CourseRegenerateRequest orderedRequest = new CourseRegenerateRequest();
            orderedRequest.setBase(request);
            orderedRequest.setEntries(request.getPreferenceEntries());
            return regenerate(orderedRequest);
        }

        RegionSeedData.RegionMeta meta = resolveDestination(request);
        StartPoint startPoint = resolveStart(request, meta);
        SelectedTransport selectedTransport = resolveSelectedTransport(request);
        LocalTime start = request.getDepartureTime() != null ? request.getDepartureTime() : DEFAULT_DEPARTURE;
        LocalTime end = request.getReturnTime() != null ? request.getReturnTime() : DEFAULT_RETURN;
        if (!end.isAfter(start)) {
            throw GadangException.badRequest("귀가 시간은 출발 시간보다 늦어야 합니다.");
        }
        List<Anchor> anchors = validateAnchors(request.getFixedPlaces(), start, end);
        List<RequiredSlot> requiredSlots = requiredSlots(request);
        if (anchors.size() + requiredSlots.size() > MAX_PLACES) {
            throw GadangException.badRequest("고정 장소와 필수 식사/카페 슬롯이 최대 장소 수를 초과합니다.");
        }

        int candidateRadiusMeters = candidateSearchRadiusMeters(meta);
        List<String> categories = categories(request);
        List<PlaceCandidate> candidates = courseCandidates(
                meta.hubLat, meta.hubLng, candidateRadiusMeters, categories, request.getRegion());
        RankingResult ranking = topK(candidates, request, meta);
        List<PlaceCandidate> topCandidates = removeAnchorDuplicates(ranking.candidates(), anchors);

        if (topCandidates.isEmpty() && anchors.isEmpty() && requiredSlots.isEmpty()) {
            throw GadangException.badRequest("조건에 맞는 장소 후보가 없습니다. 목적지 지역이나 활동/카페/식사 조건을 조정해 주세요.");
        }

        List<String> warnings = new ArrayList<>();
        if (topCandidates.isEmpty()) {
            warnings.add("조건에 맞는 장소 후보가 없습니다.");
        } else if (topCandidates.size() < TOP_K) {
            warnings.add("장소 후보가 적어 가능한 범위 안에서 코스를 구성했습니다.");
        }

        BuildResult built = buildGreedyCourse(request, meta, startPoint, selectedTransport, start, end, topCandidates, anchors, requiredSlots,
                ranking.adjustedScores());
        warnings.addAll(built.warnings);

        int totalCost = built.items.stream()
                .mapToInt(item -> value(item.fare()) + value(item.fee()))
                .sum();
        int totalMin = built.items.stream()
                .mapToInt(item -> value(item.min()) + value(item.stay()))
                .sum();

        Integer budget = request.getBudgetGuide();
        if (budget != null && budget > 0 && totalCost > budget) {
            warnings.add("예상 비용이 예산 가이드 " + budget + "원을 초과합니다.");
        }
        addTimelineWarnings(warnings, built.items, end);

        return new CourseResponse(
                request.getRegion() + " 추천 코스",
                request.getRegion(),
                startPoint.name(),
                start.toString(),
                built.endTime.toString(),
                totalMin,
                built.placeCount,
                totalCost,
                transportSelection(selectedTransport),
                built.items,
                warnings
        );
    }

    public CourseResponse regenerate(CourseRegenerateRequest request) {
        if (request == null || request.getBase() == null) {
            throw GadangException.badRequest("재생성 요청에는 base 조건이 필요합니다.");
        }
        CourseRequest base = request.getBase();
        List<EditEntry> entries = request.getEntries() == null ? List.of() : request.getEntries();
        if (entries.size() > MAX_PLACES) {
            throw GadangException.badRequest("편집된 코스는 최대 " + MAX_PLACES + "개 장소까지 지원됩니다.");
        }

        RegionSeedData.RegionMeta meta = resolveDestination(base);
        StartPoint startPoint = resolveStart(base, meta);
        SelectedTransport selectedTransport = resolveSelectedTransport(base);
        LocalTime start = base.getDepartureTime() != null ? base.getDepartureTime() : DEFAULT_DEPARTURE;
        LocalTime end = base.getReturnTime() != null ? base.getReturnTime() : DEFAULT_RETURN;
        if (!end.isAfter(start)) {
            throw GadangException.badRequest("귀가 시간은 출발 시간보다 늦어야 합니다.");
        }

        List<CourseResponse.CourseItem> items = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Set<String> usedKeys = new LinkedHashSet<>();
        for (EditEntry entry : entries) {
            if (isConcreteEntry(entry)) {
                ConcretePlace place = concretePlace(entry);
                usedKeys.add(concreteKey(place.pid(), place.name(), place.lat(), place.lng()));
            }
        }

        List<String> slotCategories = regenerateCategories(entries);
        List<PlaceCandidate> remaining = new ArrayList<>();
        Map<PlaceCandidate, Double> adjustedScores = new HashMap<>();
        if (!slotCategories.isEmpty()) {
            int radius = candidateSearchRadiusMeters(meta);
            List<PlaceCandidate> fetched = courseCandidates(meta.hubLat, meta.hubLng, radius, slotCategories, base.getRegion());
            for (RankedCandidate ranked : dedupeAndRank(fetched, meta)) {
                if (slotCategories.contains(ranked.candidate().getCategoryCode())
                        && !usedKeys.contains(candidateKey(ranked.candidate()))) {
                    remaining.add(ranked.candidate());
                    adjustedScores.put(ranked.candidate(), ranked.adjustedScore());
                }
            }
        }

        LocalTime currentTime = start;
        String currentName = startPoint.name();
        double currentLat = startPoint.lat();
        double currentLng = startPoint.lng();
        int placeCount = 0;

        if (selectedTransport != null) {
            currentTime = addSelectedOutbound(items, selectedTransport, currentName, currentTime, end);
            currentName = selectedTransport.toHub();
            currentLat = meta.hubLat;
            currentLng = meta.hubLng;
        } else if (haversineKm(currentLat, currentLng, meta.hubLat, meta.hubLng) >= 0.05) {
            TravelLeg outbound = travel(currentLat, currentLng, meta.hubLat, meta.hubLng);
            LocalTime arrival = currentTime.plusMinutes(outbound.minutes);
            items.add(CourseResponse.CourseItem.transit(
                    outbound.mode,
                    currentName,
                    meta.hubName,
                    currentTime.toString(),
                    arrival.toString(),
                    outbound.minutes,
                    outbound.fare
            ));
            currentTime = arrival;
            currentName = meta.hubName;
            currentLat = meta.hubLat;
            currentLng = meta.hubLng;
        }

        for (int i = 0; i < entries.size(); i++) {
            EditEntry entry = entries.get(i);
            if (entry == null || entry.getType() == null) continue;
            ConcretePlace nextTarget = nextConcrete(entries, i + 1, selectedTransport, startPoint, meta);

            if (isConcreteEntry(entry)) {
                ConcretePlace place = concretePlace(entry);
                TravelLeg leg = travel(currentLat, currentLng, place.lat(), place.lng());
                LocalTime arrival = currentTime.plusMinutes(leg.minutes);
                items.add(CourseResponse.CourseItem.transit(
                        leg.mode,
                        currentName,
                        place.name(),
                        currentTime.toString(),
                        arrival.toString(),
                        leg.minutes,
                        leg.fare
                ));
                placeCount++;
                items.add(placeItem(
                        place.pid(),
                        place.name(),
                        place.cat(),
                        arrival,
                        place.stay(),
                        place.fee(),
                        place.feeType(),
                        "편집된 코스에서 유지한 장소",
                        place.meal(),
                        place.lat(),
                        place.lng(),
                        placeCount,
                        place.role(),
                        "사용자 유지"
                ));
                currentTime = arrival.plusMinutes(place.stay());
                currentName = place.name();
                currentLat = place.lat();
                currentLng = place.lng();
                continue;
            }

            String category = regenerateSlotCategory(entry);
            if (category == null) continue;
            List<PlaceCandidate> matching = remaining.stream()
                    .filter(candidate -> category.equals(candidate.getCategoryCode()))
                    .filter(candidate -> !usedKeys.contains(candidateKey(candidate)))
                    .filter(candidate -> !sameEditEntryPlace(entry, candidate))
                    .toList();
            matching = preferEditFoodType(entry, matching);
            if (matching.isEmpty()) {
                warnings.add(regenerateEntryLabel(entry) + " 후보 장소가 없어 건너뛰었습니다.");
                continue;
            }
            RegeneratePlan planned = regeneratePlan(entry, matching, currentTime, currentLat, currentLng,
                    nextTarget.lat(), nextTarget.lng(), adjustedScores);
            if (planned == null) {
                warnings.add(regenerateSlotUnavailableMessage(entry, currentTime, matching.size()));
                continue;
            }
            PlaceCandidate selected = planned.candidate();
            TravelLeg leg = travel(currentLat, currentLng, selected.getLat(), selected.getLng());
            LocalTime arrival = planned.arrival();
            int stay = planned.stay();
            items.add(CourseResponse.CourseItem.transit(
                    leg.mode,
                    currentName,
                    selected.getName(),
                    arrival.minusMinutes(leg.minutes).toString(),
                    arrival.toString(),
                    leg.minutes,
                    leg.fare
            ));
            placeCount++;
            items.add(placeItem(
                    selected.getKakaoPlaceId(),
                    selected.getName(),
                    categoryKey(selected.getCategoryCode()),
                    arrival,
                    stay,
                    regenerateSlotCost(entry, selected),
                    feeType(selected),
                    regenerateEntryLabel(entry) + " 편집 슬롯",
                    regenerateSlotMeal(entry),
                    selected.getLat(),
                    selected.getLng(),
                    placeCount,
                    regenerateSlotRole(entry),
                    "편집 슬롯 재생성"
            ));
            currentTime = arrival.plusMinutes(stay);
            currentName = selected.getName();
            currentLat = selected.getLat();
            currentLng = selected.getLng();
            usedKeys.add(candidateKey(selected));
            remaining.remove(selected);
        }

        if (selectedTransport != null) {
            if (haversineKm(currentLat, currentLng, meta.hubLat, meta.hubLng) >= 0.05) {
                TravelLeg toHub = travel(currentLat, currentLng, meta.hubLat, meta.hubLng);
                LocalTime hubArrival = currentTime.plusMinutes(toHub.minutes);
                items.add(CourseResponse.CourseItem.transit(
                        toHub.mode,
                        currentName,
                        selectedTransport.toHub(),
                        currentTime.toString(),
                        hubArrival.toString(),
                        toHub.minutes,
                        toHub.fare
                ));
                currentTime = hubArrival;
                currentName = selectedTransport.toHub();
            }
            currentTime = addSelectedReturn(items, selectedTransport, startPoint.name(), currentTime, end);
        } else if (placeCount > 0 || haversineKm(currentLat, currentLng, startPoint.lat(), startPoint.lng()) >= 0.05) {
            TravelLeg back = travel(currentLat, currentLng, startPoint.lat(), startPoint.lng());
            LocalTime arrival = currentTime.plusMinutes(back.minutes);
            items.add(CourseResponse.CourseItem.transit(
                    back.mode,
                    currentName,
                    startPoint.name(),
                    currentTime.toString(),
                    arrival.toString(),
                    back.minutes,
                    back.fare
            ));
            currentTime = arrival;
        }

        int totalCost = items.stream()
                .mapToInt(item -> value(item.fare()) + value(item.fee()))
                .sum();
        int totalMin = items.stream()
                .mapToInt(item -> value(item.min()) + value(item.stay()))
                .sum();
        addTimelineWarnings(warnings, items, end);

        return new CourseResponse(
                base.getRegion() + " 편집 코스",
                base.getRegion(),
                startPoint.name(),
                start.toString(),
                currentTime.toString(),
                totalMin,
                placeCount,
                totalCost,
                transportSelection(selectedTransport),
                items,
                warnings
        );
    }

    private boolean isConcreteEntry(EditEntry entry) {
        return entry != null
                && (entry.getType() == EntryType.LOCKED_PLACE || entry.getType() == EntryType.SPECIFIC_PLACE);
    }

    private ConcretePlace concretePlace(EditEntry entry) {
        if (entry.getPlaceName() == null || entry.getPlaceName().isBlank()
                || entry.getLat() == null || entry.getLng() == null) {
            throw GadangException.badRequest("고정 장소는 placeName, lat, lng가 필요합니다.");
        }
        int stay = Math.max(20, value(entry.getStayMinutes()) > 0 ? entry.getStayMinutes() : 60);
        int fee = Math.max(0, value(entry.getFee()));
        String feeType = firstNonBlank(entry.getFeeType(), fee > 0 ? "estimate" : "free");
        String cat = firstNonBlank(entry.getCat(), "sight");
        String role = firstNonBlank(entry.getRole(), entry.getType() == EntryType.SPECIFIC_PLACE ? "ANCHOR" : "LOCKED");
        return new ConcretePlace(
                entry.getPid(),
                entry.getPlaceName(),
                cat,
                entry.getLat(),
                entry.getLng(),
                stay,
                fee,
                feeType,
                role,
                entry.getMeal()
        );
    }

    private ConcretePlace nextConcrete(List<EditEntry> entries,
                                       int startIndex,
                                       SelectedTransport selectedTransport,
                                       StartPoint startPoint,
                                       RegionSeedData.RegionMeta meta) {
        for (int i = startIndex; i < entries.size(); i++) {
            EditEntry entry = entries.get(i);
            if (isConcreteEntry(entry)) {
                return concretePlace(entry);
            }
        }
        if (selectedTransport != null) {
            return new ConcretePlace(null, selectedTransport.toHub(), "sight", meta.hubLat, meta.hubLng,
                    0, 0, "free", "TARGET", null);
        }
        return new ConcretePlace(null, startPoint.name(), "sight", startPoint.lat(), startPoint.lng(),
                0, 0, "free", "TARGET", null);
    }

    private String concreteKey(String pid, String name, double lat, double lng) {
        if (pid != null && !pid.isBlank()) return pid;
        return (name == null ? "" : name.toLowerCase()) + "@"
                + Math.round(lat * 10000) + "," + Math.round(lng * 10000);
    }

    private List<String> regenerateCategories(List<EditEntry> entries) {
        Set<String> categories = new LinkedHashSet<>();
        for (EditEntry entry : entries) {
            String category = regenerateSlotCategory(entry);
            if (category != null) categories.add(category);
        }
        return new ArrayList<>(categories);
    }

    private String regenerateSlotCategory(EditEntry entry) {
        if (entry == null || entry.getType() == null) return null;
        return switch (entry.getType()) {
            case ACTIVITY_SLOT -> entry.getActivityType() != null ? entry.getActivityType().kakaoCode : "AT4";
            case CAFE_SLOT -> "CE7";
            case LUNCH, DINNER -> "FD6";
            default -> null;
        };
    }

    private List<PlaceCandidate> preferEditFoodType(EditEntry entry, List<PlaceCandidate> candidates) {
        if (entry == null || entry.getFoodType() == null || entry.getFoodType() == MealConfig.FoodType.ANY) {
            return candidates;
        }
        if (entry.getType() != EntryType.LUNCH && entry.getType() != EntryType.DINNER) {
            return candidates;
        }
        List<PlaceCandidate> matching = candidates.stream()
                .filter(candidate -> matchesFoodType(candidate, entry.getFoodType()))
                .toList();
        return matching.isEmpty() ? candidates : matching;
    }

    private boolean sameEditEntryPlace(EditEntry entry, PlaceCandidate candidate) {
        if (entry == null || candidate == null) return false;
        if (entry.getPid() != null && !entry.getPid().isBlank()
                && entry.getPid().equals(candidate.getKakaoPlaceId())) {
            return true;
        }
        if (entry.getPlaceName() != null && candidate.getName() != null
                && entry.getPlaceName().equalsIgnoreCase(candidate.getName())) {
            return true;
        }
        if (entry.getLat() != null && entry.getLng() != null) {
            return haversineKm(entry.getLat(), entry.getLng(), candidate.getLat(), candidate.getLng()) < 0.05;
        }
        return false;
    }

    private int regenerateSlotStay(EditEntry entry, PlaceCandidate candidate) {
        return switch (entry.getType()) {
            case CAFE_SLOT -> 45;
            case LUNCH, DINNER -> 60;
            case ACTIVITY_SLOT -> Math.max(20, candidate.getDefaultStayMinutes());
            default -> Math.max(20, candidate.getDefaultStayMinutes());
        };
    }

    private int regenerateSlotCost(EditEntry entry, PlaceCandidate candidate) {
        return switch (entry.getType()) {
            case CAFE_SLOT -> DEFAULT_CAFE_COST;
            case LUNCH -> mealEstimate(DEFAULT_LUNCH_COST, entry.getPriceLevel());
            case DINNER -> mealEstimate(DEFAULT_DINNER_COST, entry.getPriceLevel());
            default -> Math.max(0, candidate.getAdmissionFee());
        };
    }

    private RegeneratePlan regeneratePlan(EditEntry entry,
                                          List<PlaceCandidate> matching,
                                          LocalTime currentTime,
                                          double currentLat,
                                          double currentLng,
                                          double targetLat,
                                          double targetLng,
                                          Map<PlaceCandidate, Double> adjustedScores) {
        TimeWindow window = regenerateWindow(entry);
        if (window == null) {
            PlaceCandidate selected = bestOptionalCandidate(
                    currentLat, currentLng, targetLat, targetLng, matching, adjustedScores);
            TravelLeg leg = travel(currentLat, currentLng, selected.getLat(), selected.getLng());
            return new RegeneratePlan(selected, currentTime.plusMinutes(leg.minutes), regenerateSlotStay(entry, selected));
        }

        return matching.stream()
                .map(candidate -> regeneratePlanForWindow(entry, candidate, currentTime, currentLat, currentLng,
                        targetLat, targetLng, window))
                .filter(plan -> plan != null)
                .max(Comparator.comparingDouble(plan -> regenerateSlotSelectionScore(
                        entry, plan, currentLat, currentLng, targetLat, targetLng, adjustedScores)))
                .orElse(null);
    }

    private RegeneratePlan regeneratePlanForWindow(EditEntry entry,
                                                   PlaceCandidate candidate,
                                                   LocalTime currentTime,
                                                   double currentLat,
                                                   double currentLng,
                                                   double targetLat,
                                                   double targetLng,
                                                   TimeWindow window) {
        TravelLeg outbound = travel(currentLat, currentLng, candidate.getLat(), candidate.getLng());
        LocalTime arrival = currentTime.plusMinutes(outbound.minutes);
        if (arrival.isBefore(window.start())) {
            arrival = window.start();
        }
        int stay = regenerateSlotStay(entry, candidate);
        if (arrival.plusMinutes(stay).isAfter(window.end())) {
            return null;
        }
        return new RegeneratePlan(candidate, arrival, stay);
    }

    private TimeWindow regenerateWindow(EditEntry entry) {
        if (entry == null || entry.getType() == null) return null;
        return switch (entry.getType()) {
            case LUNCH -> new TimeWindow(LUNCH_START, LUNCH_END);
            case DINNER -> new TimeWindow(DINNER_START, DINNER_END);
            case CAFE_SLOT -> new TimeWindow(CAFE_START, CAFE_END);
            default -> null;
        };
    }

    private String regenerateSlotRole(EditEntry entry) {
        return switch (entry.getType()) {
            case CAFE_SLOT -> "CAFE";
            case LUNCH -> "LUNCH";
            case DINNER -> "DINNER";
            default -> "OPTIONAL";
        };
    }

    private String regenerateSlotMeal(EditEntry entry) {
        return switch (entry.getType()) {
            case LUNCH -> "점심";
            case DINNER -> "저녁";
            default -> null;
        };
    }

    private String regenerateEntryLabel(EditEntry entry) {
        return switch (entry.getType()) {
            case CAFE_SLOT -> "카페";
            case LUNCH -> "점심";
            case DINNER -> "저녁";
            case ACTIVITY_SLOT -> "활동";
            default -> "편집 항목";
        };
    }

    private CourseResponse.TransportSelection transportSelection(SelectedTransport transport) {
        if (transport == null) return null;
        return new CourseResponse.TransportSelection(
                transport.mode(),
                transport.fromHub(),
                transport.toHub(),
                transport.oneWayMinutes(),
                transport.fare(),
                transport.originToHubMinutes(),
                transport.originToHubFare()
        );
    }

    private List<PlaceCandidate> courseCandidates(double lat, double lng,
                                                  int radiusMeters, List<String> categories,
                                                  String regionHint) {
        if (scoredPlaceProvider instanceof CourseCandidateProvider courseCandidateProvider) {
            return courseCandidateProvider.getCourseCandidates(lat, lng, radiusMeters, categories, regionHint);
        }
        return scoredPlaceProvider.getScoredPlaces(lat, lng, radiusMeters, categories, regionHint);
    }

    private RegionSeedData.RegionMeta resolveRegion(String region) {
        if (region == null || region.isBlank()) {
            throw GadangException.badRequest("region은 필수입니다.");
        }
        RegionSeedData.RegionMeta meta = RegionSeedData.REGION_META.get(region);
        if (meta == null) {
            throw GadangException.badRequest("지원하지 않는 지역: " + region);
        }
        return meta;
    }

    private RegionSeedData.RegionMeta resolveDestination(CourseRequest request) {
        if (request.getDestinationLat() != null && request.getDestinationLng() != null) {
            String name = firstNonBlank(request.getDestinationAddress(), request.getRegion(), "목적지");
            return new RegionSeedData.RegionMeta(
                    request.getDestinationLat(),
                    request.getDestinationLng(),
                    "",
                    "LOCAL",
                    List.of("목적지"),
                    "목적지 기반 코스",
                    request.getDestinationLat(),
                    request.getDestinationLng(),
                    0,
                    0,
                    name
            );
        }
        return resolveRegion(request.getRegion());
    }

    private StartPoint resolveStart(CourseRequest request, RegionSeedData.RegionMeta destination) {
        String name = firstNonBlank(request.getStartAddress(), "출발지");
        if (request.getStartLat() != null && request.getStartLng() != null) {
            return new StartPoint(name, request.getStartLat(), request.getStartLng());
        }

        RegionSeedData.RegionMeta startRegion = findRegionMeta(request.getStartAddress());
        if (startRegion != null) {
            return new StartPoint(name, startRegion.hubLat, startRegion.hubLng);
        }

        return new StartPoint(destination.hubName, destination.hubLat, destination.hubLng);
    }

    private SelectedTransport resolveSelectedTransport(CourseRequest request) {
        if (request.getTransportOneWayMin() == null || request.getTransportOneWayMin() <= 0) {
            return null;
        }
        String mode = firstNonBlank(request.getTransportMode(), "대중교통");
        String fromHub = firstNonBlank(request.getTransportFromHub(), request.getStartAddress(), "출발 허브");
        String toHub = firstNonBlank(request.getTransportToHub(), request.getDestinationAddress(), request.getRegion(), "도착 허브");
        int oneWay = Math.max(1, request.getTransportOneWayMin());
        int originToHub = Math.max(0, value(request.getTransportOriginToHubMin()));
        if (originToHub >= oneWay) {
            originToHub = 0;
        }
        int mainMinutes = Math.max(1, oneWay - originToHub);
        int fare = Math.max(0, value(request.getTransportFare()));
        int originToHubFare = Math.max(0, value(request.getTransportOriginToHubFare()));
        return new SelectedTransport(mode, fromHub, toHub, oneWay, mainMinutes, originToHub, originToHubFare, fare);
    }

    private int candidateSearchRadiusMeters(RegionSeedData.RegionMeta meta) {
        int hubToRegionCenterMeters = (int) Math.round(haversineKm(meta.hubLat, meta.hubLng, meta.lat, meta.lng) * 1000);
        int radius = Math.max(MIN_CANDIDATE_RADIUS_METERS, hubToRegionCenterMeters + 10_000);
        return Math.min(MAX_CANDIDATE_RADIUS_METERS, radius);
    }

    private RegionSeedData.RegionMeta findRegionMeta(String value) {
        if (value == null || value.isBlank()) return null;
        RegionSeedData.RegionMeta exact = RegionSeedData.REGION_META.get(value);
        if (exact != null) return exact;
        for (Map.Entry<String, RegionSeedData.RegionMeta> entry : RegionSeedData.REGION_META.entrySet()) {
            if (value.contains(entry.getKey())) return entry.getValue();
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }

    private List<String> categories(CourseRequest request) {
        Set<String> categories = new LinkedHashSet<>();
        if (request.getActivityTypes() != null) {
            for (ActivityType type : request.getActivityTypes()) {
                if (type != null) categories.add(type.kakaoCode);
            }
        }
        if (cafeSlotCount(request) > 0) categories.add("CE7");
        if (enabled(request.getLunch()) || enabled(request.getDinner())) categories.add("FD6");
        return categories.isEmpty() ? null : new ArrayList<>(categories);
    }

    private RankingResult topK(List<PlaceCandidate> candidates,
                               CourseRequest request,
                               RegionSeedData.RegionMeta meta) {
        if (candidates == null || candidates.isEmpty()) {
            return new RankingResult(List.of(), Map.of());
        }

        List<RankedCandidate> ranked = dedupeAndRank(candidates, meta);
        List<String> allowedCategories = categories(request);
        if (allowedCategories != null) {
            Set<String> allowed = new LinkedHashSet<>(allowedCategories);
            ranked = new ArrayList<>(ranked.stream()
                    .filter(candidate -> allowed.contains(candidate.candidate().getCategoryCode()))
                    .toList());
        }
        ranked.sort(Comparator.comparingDouble(RankedCandidate::adjustedScore).reversed());

        Map<PlaceCandidate, Double> adjustedScores = new HashMap<>();
        for (RankedCandidate candidate : ranked) {
            adjustedScores.put(candidate.candidate(), candidate.adjustedScore());
        }

        List<PlaceCandidate> result = new ArrayList<>();
        Set<String> selectedKeys = new LinkedHashSet<>();

        int mealSlotCount = (enabled(request.getLunch()) ? 1 : 0) + (enabled(request.getDinner()) ? 1 : 0);
        if (mealSlotCount > 0) {
            addMealTypeQuota(result, selectedKeys, ranked, request.getLunch(), 1);
            addMealTypeQuota(result, selectedKeys, ranked, request.getDinner(), 1);
            addCategoryQuota(result, selectedKeys, ranked, "FD6", mealSlotCount + 1);
        }
        int cafeSlotCount = cafeSlotCount(request);
        if (cafeSlotCount > 0) {
            addCategoryQuota(result, selectedKeys, ranked, "CE7", cafeSlotCount + 1);
        }

        int activityQuota = hasExplicitActivityTypes(request) ? 1 : 2;
        for (String category : activityQuotaCategories(request, ranked)) {
            addCategoryQuota(result, selectedKeys, ranked, category, activityQuota);
        }

        for (RankedCandidate candidate : ranked) {
            if (result.size() >= TOP_K) break;
            if (selectedKeys.add(candidate.key())) {
                result.add(candidate.candidate());
            }
        }

        return new RankingResult(result, adjustedScores);
    }

    private List<RankedCandidate> dedupeAndRank(List<PlaceCandidate> candidates,
                                                RegionSeedData.RegionMeta meta) {
        Map<String, RankedCandidate> bestByKey = new java.util.LinkedHashMap<>();
        for (PlaceCandidate candidate : candidates) {
            if (candidate == null) continue;
            String key = candidateKey(candidate);
            double adjusted = adjustedScore(candidate, meta);
            RankedCandidate ranked = new RankedCandidate(candidate, key, adjusted);
            RankedCandidate existing = bestByKey.get(key);
            if (existing == null || ranked.adjustedScore() > existing.adjustedScore()) {
                bestByKey.put(key, ranked);
            }
        }
        return new ArrayList<>(bestByKey.values());
    }

    private double adjustedScore(PlaceCandidate candidate, RegionSeedData.RegionMeta meta) {
        double centerDistanceKm = haversineKm(candidate.getLat(), candidate.getLng(), meta.lat, meta.lng);
        double hubDistanceKm = haversineKm(candidate.getLat(), candidate.getLng(), meta.hubLat, meta.hubLng);
        return candidate.getFinalScore()
                - centerDistanceKm * 1.5
                - hubDistanceKm * 0.5
                + randomJitter(RANKING_RANDOM_SPREAD);
    }

    private void addCategoryQuota(List<PlaceCandidate> result,
                                  Set<String> selectedKeys,
                                  List<RankedCandidate> ranked,
                                  String categoryCode,
                                  int quota) {
        if (result.size() >= TOP_K || quota <= 0) return;
        int added = 0;
        for (RankedCandidate candidate : ranked) {
            if (result.size() >= TOP_K || added >= quota) break;
            if (!categoryCode.equals(candidate.candidate().getCategoryCode())) continue;
            if (selectedKeys.add(candidate.key())) {
                result.add(candidate.candidate());
                added++;
            }
        }
    }

    private void addMealTypeQuota(List<PlaceCandidate> result,
                                  Set<String> selectedKeys,
                                  List<RankedCandidate> ranked,
                                  MealConfig meal,
                                  int quota) {
        if (!enabled(meal) || meal.getFoodType() == null || meal.getFoodType() == MealConfig.FoodType.ANY) {
            return;
        }
        int added = 0;
        for (RankedCandidate candidate : ranked) {
            if (!"FD6".equals(candidate.candidate().getCategoryCode())) continue;
            if (!matchesFoodType(candidate.candidate(), meal.getFoodType())) continue;
            if (selectedKeys.add(candidate.key())) {
                result.add(candidate.candidate());
                added++;
                if (added >= quota || result.size() >= TOP_K) return;
            }
        }
    }

    private List<String> activityQuotaCategories(CourseRequest request, List<RankedCandidate> ranked) {
        List<String> categories = activitySlotCategories(request);
        if (categories.isEmpty()) {
            categories.add("AT4");
            categories.add("CT1");
        }
        return categories.stream()
                .sorted(Comparator.comparingDouble((String category) -> bestAdjustedScore(ranked, category)).reversed())
                .toList();
    }

    private boolean hasExplicitActivityTypes(CourseRequest request) {
        return request.getActivityTypes() != null && !request.getActivityTypes().isEmpty();
    }

    private List<String> activitySlotCategories(CourseRequest request) {
        List<String> categories = new ArrayList<>();
        if (!hasExplicitActivityTypes(request)) return categories;
        for (ActivityType type : request.getActivityTypes()) {
            if (type != null && type.kakaoCode != null) {
                categories.add(type.kakaoCode);
            }
        }
        return categories;
    }

    private double bestAdjustedScore(List<RankedCandidate> ranked, String categoryCode) {
        return ranked.stream()
                .filter(candidate -> categoryCode.equals(candidate.candidate().getCategoryCode()))
                .mapToDouble(RankedCandidate::adjustedScore)
                .max()
                .orElse(Double.NEGATIVE_INFINITY);
    }

    private String candidateKey(PlaceCandidate candidate) {
        return candidate.getKakaoPlaceId() != null && !candidate.getKakaoPlaceId().isBlank()
                ? candidate.getKakaoPlaceId()
                : candidate.getName() + ":" + candidate.getLat() + ":" + candidate.getLng();
    }

    private List<Anchor> validateAnchors(List<CourseRequest.FixedPlace> fixedPlaces,
                                         LocalTime start,
                                         LocalTime end) {
        if (fixedPlaces == null || fixedPlaces.isEmpty()) return List.of();
        if (fixedPlaces.size() > MAX_ANCHORS) {
            throw GadangException.badRequest("고정 장소는 최대 " + MAX_ANCHORS + "개까지 지원됩니다.");
        }

        List<Anchor> anchors = new ArrayList<>();
        for (CourseRequest.FixedPlace fixed : fixedPlaces) {
            if (fixed == null
                    || fixed.getPlaceName() == null || fixed.getPlaceName().isBlank()
                    || fixed.getLat() == null || fixed.getLng() == null) {
                throw GadangException.badRequest("고정 장소는 placeName, lat, lng가 필요합니다.");
            }
            boolean hasVisit = fixed.getVisitTime() != null;
            boolean hasDepart = fixed.getDepartTime() != null;
            if (hasVisit != hasDepart) {
                throw GadangException.badRequest("고정 장소 '" + fixed.getPlaceName()
                        + "' 시간은 visitTime과 departTime을 함께 보내거나 둘 다 비워야 합니다.");
            }
            if (hasVisit && (!fixed.getVisitTime().isAfter(start)
                    || !fixed.getDepartTime().isAfter(fixed.getVisitTime())
                    || !fixed.getDepartTime().isBefore(end))) {
                throw GadangException.badRequest(fixedPlaceTimeError(fixed, start, end));
            }
            anchors.add(new Anchor(fixed.getPlaceName(), fixed.getLat(), fixed.getLng(),
                    fixed.getVisitTime(), fixed.getDepartTime()));
        }
        if (anchors.stream().allMatch(anchor -> anchor.visitTime() != null)) {
            anchors.sort(Comparator.comparing(Anchor::visitTime));
        }
        return anchors;
    }

    private String fixedPlaceTimeError(CourseRequest.FixedPlace fixed, LocalTime start, LocalTime end) {
        String name = fixed.getPlaceName();
        LocalTime visit = fixed.getVisitTime();
        LocalTime depart = fixed.getDepartTime();
        if (!visit.isAfter(start)) {
            return "고정 장소 '" + name + "' 방문 시간이 전체 출발 시간보다 이릅니다. "
                    + "방문 " + visit + ", 전체 일정 " + start + "~" + end
                    + ". 방문 시간은 출발 시간 이후로 설정해 주세요.";
        }
        if (!depart.isAfter(visit)) {
            return "고정 장소 '" + name + "' 출발 시간이 방문 시간보다 이르거나 같습니다. "
                    + "방문 " + visit + ", 출발 " + depart
                    + ". 출발 시간은 방문 시간 이후로 설정해 주세요.";
        }
        return "고정 장소 '" + name + "' 출발 시간이 전체 귀가 시간보다 늦습니다. "
                + "출발 " + depart + ", 전체 일정 " + start + "~" + end
                + ". 고정 장소 출발 시간은 귀가 시간 이전이어야 합니다.";
    }

    private List<RequiredSlot> requiredSlots(CourseRequest request) {
        List<RequiredSlot> slots = new ArrayList<>();
        if (enabled(request.getLunch())) {
            slots.add(new RequiredSlot("lunch", "점심", "FD6", LUNCH_START, LUNCH_END, 60, "점심",
                    mealFoodType(request.getLunch())));
        }
        List<CourseRequest.CafeConfig> cafeSlots = request.getCafeSlots();
        if (cafeSlots != null && !cafeSlots.isEmpty()) {
            for (int i = 0; i < cafeSlots.size(); i++) {
                CourseRequest.CafeConfig cafe = cafeSlots.get(i);
                LocalTime start = timeOrDefault(cafe == null ? null : cafe.getStartTime(), CAFE_START);
                LocalTime end = timeOrDefault(cafe == null ? null : cafe.getEndTime(), CAFE_END);
                validateSlotWindow("카페", start, end);
                slots.add(new RequiredSlot("cafe" + i, "카페", "CE7", start, end, 45, null, null));
            }
        } else if (request.isCafeEnabled()) {
            LocalTime start = timeOrDefault(request.getCafeStartTime(), CAFE_START);
            LocalTime end = timeOrDefault(request.getCafeEndTime(), CAFE_END);
            validateSlotWindow("카페", start, end);
            slots.add(new RequiredSlot("cafe", "카페", "CE7", start, end, 45, null, null));
        }
        if (enabled(request.getDinner())) {
            slots.add(new RequiredSlot("dinner", "저녁", "FD6", DINNER_START, DINNER_END, 60, "저녁",
                    mealFoodType(request.getDinner())));
        }
        return slots;
    }

    private MealConfig.FoodType mealFoodType(MealConfig meal) {
        return meal == null ? MealConfig.FoodType.ANY : meal.getFoodType();
    }

    private int cafeSlotCount(CourseRequest request) {
        if (request.getCafeSlots() != null && !request.getCafeSlots().isEmpty()) {
            return request.getCafeSlots().size();
        }
        return request.isCafeEnabled() ? 1 : 0;
    }

    private LocalTime timeOrDefault(LocalTime value, LocalTime fallback) {
        return value != null ? value : fallback;
    }

    private void validateSlotWindow(String label, LocalTime start, LocalTime end) {
        if (!end.isAfter(start)) {
            throw GadangException.badRequest(label + " 시간 범위가 올바르지 않습니다.");
        }
    }

    private List<PlaceCandidate> removeAnchorDuplicates(List<PlaceCandidate> candidates, List<Anchor> anchors) {
        if (anchors.isEmpty() || candidates.isEmpty()) return candidates;
        return candidates.stream()
                .filter(candidate -> anchors.stream().noneMatch(anchor -> samePlace(candidate, anchor)))
                .toList();
    }

    private boolean samePlace(PlaceCandidate candidate, Anchor anchor) {
        if (candidate.getName() != null && candidate.getName().equalsIgnoreCase(anchor.name())) {
            return true;
        }
        return haversineKm(candidate.getLat(), candidate.getLng(), anchor.lat(), anchor.lng()) < 0.05;
    }

    private BuildResult buildGreedyCourse(CourseRequest request,
                                          RegionSeedData.RegionMeta meta,
                                          StartPoint startPoint,
                                          SelectedTransport selectedTransport,
                                          LocalTime start,
                                          LocalTime end,
                                          List<PlaceCandidate> candidates,
                                          List<Anchor> anchors,
                                          List<RequiredSlot> requiredSlots,
                                          Map<PlaceCandidate, Double> adjustedScores) {
        List<CourseResponse.CourseItem> items = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<PlaceCandidate> remaining = new ArrayList<>(candidates);
        List<RequiredSlot> pendingSlots = new ArrayList<>(requiredSlots);
        List<String> pendingActivityCategories = activitySlotCategories(request);

        LocalTime currentTime = start;
        String currentName = startPoint.name();
        double currentLat = startPoint.lat();
        double currentLng = startPoint.lng();
        int placeCount = 0;

        if (selectedTransport != null) {
            currentTime = addSelectedOutbound(items, selectedTransport, currentName, currentTime, end);
            currentName = selectedTransport.toHub();
            currentLat = meta.hubLat;
            currentLng = meta.hubLng;
        } else if (haversineKm(currentLat, currentLng, meta.hubLat, meta.hubLng) >= 0.05) {
            TravelLeg outbound = travel(currentLat, currentLng, meta.hubLat, meta.hubLng);
            LocalTime arrival = currentTime.plusMinutes(outbound.minutes);
            if (arrival.isAfter(end)) {
                throw GadangException.badRequest("목적지까지 이동할 시간이 부족합니다.");
            }
            items.add(CourseResponse.CourseItem.transit(
                    outbound.mode,
                    currentName,
                    meta.hubName,
                    currentTime.toString(),
                    arrival.toString(),
                    outbound.minutes,
                    outbound.fare
            ));
            currentTime = arrival;
            currentName = meta.hubName;
            currentLat = meta.hubLat;
            currentLng = meta.hubLng;
        }

        for (int i = 0; i < anchors.size(); i++) {
            Anchor anchor = anchors.get(i);
            if (anchor.visitTime() == null) {
                TravelLeg toAnchor = travel(currentLat, currentLng, anchor.lat(), anchor.lng());
                LocalTime anchorArrival = currentTime.plusMinutes(toAnchor.minutes);
                items.add(CourseResponse.CourseItem.transit(
                        toAnchor.mode,
                        currentName,
                        anchor.name(),
                        currentTime.toString(),
                        anchorArrival.toString(),
                        toAnchor.minutes,
                        toAnchor.fare
                ));
                int anchorStay = 60;
                items.add(placeItem(
                        null,
                        anchor.name(),
                        "sight",
                        anchorArrival,
                        anchorStay,
                        0,
                        "free",
                        "고정 장소",
                        null,
                        anchor.lat(),
                        anchor.lng(),
                        placeCount + 1,
                        "ANCHOR",
                        "고정 장소"
                ));
                currentTime = anchorArrival.plusMinutes(anchorStay);
                currentName = anchor.name();
                currentLat = anchor.lat();
                currentLng = anchor.lng();
                placeCount++;
                continue;
            }

            SegmentResult segment = fillGreedySegment(
                    request, items, remaining, pendingSlots, currentName, currentLat, currentLng, currentTime,
                    anchor.name(), anchor.lat(), anchor.lng(), anchor.visitTime(), placeCount, anchors.size() - i,
                    adjustedScores, pendingActivityCategories, false);
            currentName = segment.currentName;
            currentLat = segment.currentLat;
            currentLng = segment.currentLng;
            currentTime = segment.currentTime;
            placeCount = segment.placeCount;

            TravelLeg toAnchor = travel(currentLat, currentLng, anchor.lat(), anchor.lng());
            LocalTime earliestAnchorArrival = currentTime.plusMinutes(toAnchor.minutes);
            if (earliestAnchorArrival.isAfter(anchor.visitTime())) {
                throw GadangException.badRequest("고정 장소 '" + anchor.name()
                        + "'에 " + anchor.visitTime() + "까지 도착할 수 없습니다. "
                        + "현재 위치/시간: " + currentName + " " + currentTime
                        + ", 이동 예상: " + toAnchor.minutes + "분"
                        + ", 가장 빠른 도착: " + earliestAnchorArrival + ". "
                        + "앞선 일정, 고정 장소 방문 시간, 출발 시간을 조정해 주세요.");
            }
            LocalTime anchorDeparture = anchor.visitTime().minusMinutes(toAnchor.minutes());
            items.add(CourseResponse.CourseItem.transit(
                    toAnchor.mode,
                    currentName,
                    anchor.name(),
                    anchorDeparture.toString(),
                    anchor.visitTime().toString(),
                    toAnchor.minutes,
                    toAnchor.fare
            ));
            int anchorStay = (int) java.time.Duration.between(anchor.visitTime(), anchor.departTime()).toMinutes();
            items.add(placeItem(
                    null,
                    anchor.name(),
                    "sight",
                    anchor.visitTime(),
                    anchorStay,
                    0,
                    "free",
                    "필수 도착 " + anchor.visitTime() + " · 출발 " + anchor.departTime(),
                    null,
                    anchor.lat(),
                    anchor.lng(),
                    placeCount + 1,
                    "ANCHOR",
                    "고정 장소"
            ));
            currentTime = anchor.departTime();
            currentName = anchor.name();
            currentLat = anchor.lat();
            currentLng = anchor.lng();
            placeCount++;
        }

        LocalTime finalDeadline = selectedTransport != null
                ? end.minusMinutes(selectedTransport.oneWayMinutes())
                : end;
        SegmentResult finalSegment = fillGreedySegment(
                request, items, remaining, pendingSlots, currentName, currentLat, currentLng, currentTime,
                selectedTransport != null ? selectedTransport.toHub() : startPoint.name(),
                selectedTransport != null ? meta.hubLat : startPoint.lat(),
                selectedTransport != null ? meta.hubLng : startPoint.lng(),
                finalDeadline, placeCount, 0, adjustedScores, pendingActivityCategories, true);
        currentName = finalSegment.currentName;
        currentLat = finalSegment.currentLat;
        currentLng = finalSegment.currentLng;
        currentTime = finalSegment.currentTime;
        placeCount = finalSegment.placeCount;

        if (placeCount == 0 && !candidates.isEmpty()) {
            warnings.add("시간 안에 방문 가능한 장소가 없어 허브 기준 빈 코스를 반환합니다.");
        }
        if (!pendingSlots.isEmpty()) {
            RequiredSlot slot = pendingSlots.get(0);
            throw GadangException.badRequest(requiredSlotUnavailableMessage(
                    slot, remaining, currentName, currentTime, finalDeadline));
        }

        if (selectedTransport != null) {
            if (haversineKm(currentLat, currentLng, meta.hubLat, meta.hubLng) >= 0.05) {
                TravelLeg toHub = travel(currentLat, currentLng, meta.hubLat, meta.hubLng);
                LocalTime hubArrival = currentTime.plusMinutes(toHub.minutes);
                items.add(CourseResponse.CourseItem.transit(
                        toHub.mode,
                        currentName,
                        selectedTransport.toHub(),
                        currentTime.toString(),
                        hubArrival.toString(),
                        toHub.minutes,
                        toHub.fare
                ));
                currentTime = hubArrival;
                currentName = selectedTransport.toHub();
            }
            currentTime = addSelectedReturn(items, selectedTransport, startPoint.name(), currentTime, end);
        } else if (placeCount > 0 || !anchors.isEmpty() || !requiredSlots.isEmpty()
                || haversineKm(currentLat, currentLng, startPoint.lat(), startPoint.lng()) >= 0.05) {
            TravelLeg back = travel(currentLat, currentLng, startPoint.lat(), startPoint.lng());
            LocalTime arrival = currentTime.plusMinutes(back.minutes);
            items.add(CourseResponse.CourseItem.transit(
                    back.mode,
                    currentName,
                    startPoint.name(),
                    currentTime.toString(),
                    arrival.toString(),
                    back.minutes,
                    back.fare
            ));
            currentTime = arrival;
        }

        return new BuildResult(items, warnings, currentTime, placeCount);
    }

    private LocalTime addSelectedOutbound(List<CourseResponse.CourseItem> items,
                                          SelectedTransport transport,
                                          String startName,
                                          LocalTime currentTime,
                                          LocalTime end) {
        if (transport.originToHubMinutes() > 0) {
            LocalTime hubArrival = currentTime.plusMinutes(transport.originToHubMinutes());
            if (hubArrival.isAfter(end)) {
                throw GadangException.badRequest("선택한 교통수단 출발 허브까지 이동할 시간이 부족합니다.");
            }
            items.add(CourseResponse.CourseItem.transit(
                    "대중교통",
                    startName,
                    transport.fromHub(),
                    currentTime.toString(),
                    hubArrival.toString(),
                    transport.originToHubMinutes(),
                    transport.originToHubFare()
            ));
            currentTime = hubArrival;
        }

        LocalTime arrival = currentTime.plusMinutes(transport.mainMinutes());
        if (arrival.isAfter(end)) {
            throw GadangException.badRequest("선택한 교통수단으로 목적지까지 이동할 시간이 부족합니다.");
        }
        items.add(CourseResponse.CourseItem.transit(
                transport.mode(),
                transport.fromHub(),
                transport.toHub(),
                currentTime.toString(),
                arrival.toString(),
                transport.mainMinutes(),
                transport.fare()
        ));
        return arrival;
    }

    private LocalTime addSelectedReturn(List<CourseResponse.CourseItem> items,
                                        SelectedTransport transport,
                                        String startName,
                                        LocalTime currentTime,
                                        LocalTime end) {
        LocalTime hubArrival = currentTime.plusMinutes(transport.mainMinutes());
        items.add(CourseResponse.CourseItem.transit(
                transport.mode(),
                transport.toHub(),
                transport.fromHub(),
                currentTime.toString(),
                hubArrival.toString(),
                transport.mainMinutes(),
                transport.fare()
        ));
        currentTime = hubArrival;

        if (transport.originToHubMinutes() > 0) {
            LocalTime finalArrival = currentTime.plusMinutes(transport.originToHubMinutes());
            items.add(CourseResponse.CourseItem.transit(
                    "대중교통",
                    transport.fromHub(),
                    startName,
                    currentTime.toString(),
                    finalArrival.toString(),
                    transport.originToHubMinutes(),
                    transport.originToHubFare()
            ));
            currentTime = finalArrival;
        }
        return currentTime;
    }

    private SegmentResult fillGreedySegment(CourseRequest request,
                                            List<CourseResponse.CourseItem> items,
                                            List<PlaceCandidate> remaining,
                                            List<RequiredSlot> pendingSlots,
                                            String currentName,
                                            double currentLat,
                                            double currentLng,
                                            LocalTime currentTime,
                                            String targetName,
                                            double targetLat,
                                            double targetLng,
                                            LocalTime deadline,
                                            int placeCount,
                                            int anchorsRemaining,
                                            Map<PlaceCandidate, Double> adjustedScores,
                                            List<String> pendingActivityCategories,
                                            boolean allowRequiredOverrun) {
        List<RequiredSlot> segmentSlots = new ArrayList<>();
        for (RequiredSlot slot : pendingSlots) {
            boolean belongsInSegment = allowRequiredOverrun
                    ? slot.windowEnd().isAfter(currentTime)
                    : slot.windowStart().isBefore(deadline) && slot.windowEnd().isAfter(currentTime);
            if (belongsInSegment) {
                segmentSlots.add(slot);
            }
        }
        segmentSlots.sort(Comparator.comparing(RequiredSlot::windowStart));

        for (RequiredSlot slot : segmentSlots) {
            SlotPlan planned = nearestFeasibleSlot(slot, remaining, currentTime, currentLat, currentLng,
                    targetLat, targetLng, deadline, adjustedScores);
            if (planned == null && allowRequiredOverrun) {
                planned = nearestSchedulableSlot(slot, remaining, currentTime, currentLat, currentLng,
                        adjustedScores);
            }
            if (planned == null) {
                if (!slot.windowEnd().isAfter(deadline)) {
                    throw GadangException.badRequest(requiredSlotUnavailableMessage(
                            slot, remaining, currentName, currentTime, deadline));
                }
                continue;
            }

            int placeLimit = MAX_PLACES - pendingSlots.size() - anchorsRemaining;
            SegmentResult beforeSlot = fillOptionalGreedy(
                    request, items, remaining, currentName, currentLat, currentLng, currentTime,
                    planned.candidate().getName(), planned.candidate().getLat(), planned.candidate().getLng(),
                    optionalDeadlineBeforeSlot(slot), placeCount, placeLimit, reservedCategories(pendingSlots), adjustedScores,
                    pendingActivityCategories);
            currentName = beforeSlot.currentName;
            currentLat = beforeSlot.currentLat;
            currentLng = beforeSlot.currentLng;
            currentTime = beforeSlot.currentTime;
            placeCount = beforeSlot.placeCount;

            planned = nearestFeasibleSlot(slot, remaining, currentTime, currentLat, currentLng,
                    targetLat, targetLng, deadline, adjustedScores);
            if (planned == null && allowRequiredOverrun) {
                planned = nearestSchedulableSlot(slot, remaining, currentTime, currentLat, currentLng,
                        adjustedScores);
            }
            if (planned == null) {
                throw GadangException.badRequest(requiredSlotUnavailableMessage(
                        slot, remaining, currentName, currentTime, deadline));
            }

            TravelLeg outbound = travel(currentLat, currentLng, planned.candidate().getLat(), planned.candidate().getLng());
            LocalTime dep = planned.arrival().minusMinutes(outbound.minutes);
            items.add(CourseResponse.CourseItem.transit(
                    outbound.mode,
                    currentName,
                    planned.candidate().getName(),
                    dep.toString(),
                    planned.arrival().toString(),
                    outbound.minutes,
                    outbound.fare
            ));
            items.add(placeItem(
                    planned.candidate().getKakaoPlaceId(),
                    planned.candidate().getName(),
                    categoryKey(planned.candidate().getCategoryCode()),
                    planned.arrival(),
                    planned.stay(),
                    requiredSlotCost(slot, planned.candidate(), request),
                    feeType(planned.candidate()),
                    slot.label() + " 시간대 " + slot.windowStart() + "-" + slot.windowEnd()
                            + " · 카테고리 " + slot.categoryCode(),
                    slot.mealLabel(),
                    planned.candidate().getLat(),
                    planned.candidate().getLng(),
                    placeCount + 1,
                    slotRole(slot),
                    "필수 시간 슬롯"
            ));
            currentTime = planned.arrival().plusMinutes(planned.stay());
            currentName = planned.candidate().getName();
            currentLat = planned.candidate().getLat();
            currentLng = planned.candidate().getLng();
            placeCount++;
            remaining.remove(planned.candidate());
            pendingSlots.remove(slot);
        }

        int placeLimit = MAX_PLACES - pendingSlots.size() - anchorsRemaining;
        return fillOptionalGreedy(
                request, items, remaining, currentName, currentLat, currentLng, currentTime,
                targetName, targetLat, targetLng, deadline, placeCount, placeLimit, reservedCategories(pendingSlots),
                adjustedScores, pendingActivityCategories);
    }

    private SegmentResult fillOptionalGreedy(CourseRequest request,
                                             List<CourseResponse.CourseItem> items,
                                             List<PlaceCandidate> remaining,
                                             String currentName,
                                             double currentLat,
                                             double currentLng,
                                             LocalTime currentTime,
                                             String targetName,
                                             double targetLat,
                                             double targetLng,
                                             LocalTime deadline,
                                             int placeCount,
                                             int placeLimit,
                                             Set<String> reservedCategories,
                                             Map<PlaceCandidate, Double> adjustedScores,
                                             List<String> pendingActivityCategories) {
        List<PlaceCandidate> segmentCandidates = new ArrayList<>(remaining);
        while (!segmentCandidates.isEmpty() && placeCount < placeLimit) {
            List<PlaceCandidate> available = segmentCandidates.stream()
                    .filter(candidate -> !reservedCategories.contains(candidate.getCategoryCode()))
                    .toList();
            if (available.isEmpty()) {
                break;
            }
            PlaceCandidate next = bestRequestedActivityCandidate(currentLat, currentLng, targetLat, targetLng,
                    available, adjustedScores, pendingActivityCategories);
            if (next == null) {
                next = bestOptionalCandidate(currentLat, currentLng, targetLat, targetLng,
                        available, adjustedScores);
            }
            TravelLeg outbound = travel(currentLat, currentLng, next.getLat(), next.getLng());
            TravelLeg toTarget = travel(next.getLat(), next.getLng(), targetLat, targetLng);
            int stay = Math.max(20, next.getDefaultStayMinutes());

            LocalTime placeArrival = currentTime.plusMinutes(outbound.minutes);
            LocalTime afterStay = placeArrival.plusMinutes(stay);
            if (!afterStay.plusMinutes(toTarget.minutes).isAfter(deadline)) {
                items.add(CourseResponse.CourseItem.transit(
                        outbound.mode,
                        currentName,
                        next.getName(),
                        currentTime.toString(),
                        placeArrival.toString(),
                        outbound.minutes,
                        outbound.fare
                ));
                items.add(placeItem(
                        next.getKakaoPlaceId(),
                        next.getName(),
                        categoryKey(next.getCategoryCode()),
                        placeArrival,
                        stay,
                        placeCost(next, request),
                        feeType(next),
                        optionalPlaceNote(next, targetName),
                        null,
                        next.getLat(),
                        next.getLng(),
                        placeCount + 1,
                        "OPTIONAL",
                        "동선 최적화 선택"
                ));

                currentTime = afterStay;
                currentName = next.getName();
                currentLat = next.getLat();
                currentLng = next.getLng();
                placeCount++;
                remaining.remove(next);
                pendingActivityCategories.remove(next.getCategoryCode());
            }
            segmentCandidates.remove(next);
        }
        return new SegmentResult(currentName, currentLat, currentLng, currentTime, placeCount);
    }

    private SlotPlan nearestFeasibleSlot(RequiredSlot slot,
                                         List<PlaceCandidate> remaining,
                                         LocalTime currentTime,
                                         double currentLat,
                                         double currentLng,
                                         double targetLat,
                                         double targetLng,
                                         LocalTime deadline,
                                         Map<PlaceCandidate, Double> adjustedScores) {
        return slotCandidates(slot, remaining).stream()
                .map(candidate -> slotPlan(slot, candidate, currentTime, currentLat, currentLng,
                        targetLat, targetLng, deadline))
                .filter(plan -> plan != null)
                .max(Comparator.comparingDouble(plan -> slotSelectionScore(slot, plan, currentLat, currentLng, adjustedScores)))
                .orElse(null);
    }

    private SlotPlan nearestSchedulableSlot(RequiredSlot slot,
                                            List<PlaceCandidate> remaining,
                                            LocalTime currentTime,
                                            double currentLat,
                                            double currentLng,
                                            Map<PlaceCandidate, Double> adjustedScores) {
        return slotCandidates(slot, remaining).stream()
                .map(candidate -> slotPlan(slot, candidate, currentTime, currentLat, currentLng,
                        0, 0, null))
                .filter(plan -> plan != null)
                .max(Comparator.comparingDouble(plan -> slotSelectionScore(slot, plan, currentLat, currentLng, adjustedScores)))
                .orElse(null);
    }

    private List<PlaceCandidate> slotCandidates(RequiredSlot slot, List<PlaceCandidate> candidates) {
        List<PlaceCandidate> categoryMatches = candidates.stream()
                .filter(candidate -> slot.categoryCode().equals(candidate.getCategoryCode()))
                .toList();
        if (!isFoodTypeSlot(slot)) {
            return categoryMatches;
        }
        List<PlaceCandidate> foodTypeMatches = categoryMatches.stream()
                .filter(candidate -> matchesFoodType(candidate, slot.foodType()))
                .toList();
        return foodTypeMatches.isEmpty() ? categoryMatches : foodTypeMatches;
    }

    private boolean isFoodTypeSlot(RequiredSlot slot) {
        return "FD6".equals(slot.categoryCode())
                && slot.foodType() != null
                && slot.foodType() != MealConfig.FoodType.ANY;
    }

    private LocalTime optionalDeadlineBeforeSlot(RequiredSlot slot) {
        if (!fixedMealSlot(slot)) {
            return slot.windowEnd();
        }
        return slot.windowEnd().minusMinutes(slot.minStayMinutes());
    }

    private boolean matchesFoodType(PlaceCandidate candidate, MealConfig.FoodType foodType) {
        if (candidate == null || foodType == null || foodType == MealConfig.FoodType.ANY) {
            return true;
        }
        String text = (safe(candidate.getName()) + " "
                + safe(candidate.getCategoryName()) + " "
                + safe(candidate.getSubCategory())).toLowerCase(java.util.Locale.ROOT);
        return switch (foodType) {
            case KOREAN -> containsAny(text,
                    "\uD55C\uC2DD", "\uBC31\uBC18", "\uAD6D\uBC25", "\uCC0C\uAC1C",
                    "\uC21C\uB450\uBD80", "\uBE44\uBE54\uBC25", "\uBD88\uACE0\uAE30",
                    "\uAC08\uBE44\uD0D5", "\uB0C9\uBA74", "\uC0BC\uACC4\uD0D5",
                    "\uBCF4\uC308", "\uC871\uBC1C", "\uD574\uC7A5\uAD6D", "\uACF0\uD0D5");
            case JAPANESE -> containsAny(text,
                    "\uC77C\uC2DD", "\uCD08\uBC25", "\uC2A4\uC2DC", "\uB77C\uBA58",
                    "\uC6B0\uB3D9", "\uB3C8\uCE74\uCE20", "\uB3C8\uAE4C\uC2A4",
                    "\uC0AC\uC2DC\uBBF8", "\uC774\uC790\uCE74\uC57C");
            case CHINESE -> containsAny(text,
                    "\uC911\uC2DD", "\uC911\uAD6D", "\uC9DC\uC7A5", "\uC9EC\uBED5",
                    "\uD0D5\uC218\uC721", "\uB9C8\uB77C");
            case WESTERN -> containsAny(text,
                    "\uC591\uC2DD", "\uD30C\uC2A4\uD0C0", "\uD53C\uC790",
                    "\uC2A4\uD14C\uC774\uD06C", "\uBE0C\uB7F0\uCE58",
                    "\uB808\uC2A4\uD1A0\uB791");
            case BUNSIK -> containsAny(text,
                    "\uBD84\uC2DD", "\uB5A1\uBCF6\uC774", "\uAE40\uBC25",
                    "\uB77C\uBCF6\uC774", "\uC21C\uB300", "\uD280\uAE40");
            case MEAT_GRILL -> containsAny(text,
                    "\uACE0\uAE30", "\uC0BC\uACB9\uC0B4", "\uAC08\uBE44", "\uAD6C\uC774",
                    "\uD55C\uC6B0", "\uC22F\uBD88", "\uBC14\uBCA0\uD050",
                    "\uB3FC\uC9C0", "\uC18C\uACE0\uAE30");
            case SEAFOOD -> containsAny(text,
                    "\uD574\uC0B0\uBB3C", "\uD69F\uC9D1", "\uD68C", "\uC870\uAC1C",
                    "\uC0DD\uC120", "\uBB3C\uD68C", "\uB300\uAC8C", "\uC7A5\uC5B4",
                    "\uD574\uBB3C");
            case FASTFOOD -> containsAny(text,
                    "\uD328\uC2A4\uD2B8\uD478\uB4DC", "\uD584\uBC84\uAC70",
                    "\uBC84\uAC70", "\uCE58\uD0A8", "\uC0CC\uB4DC\uC704\uCE58");
            case LOCAL_SPECIALTY -> containsAny(text,
                    "\uD5A5\uD1A0", "\uD1A0\uC18D", "\uC804\uD1B5", "\uC2DC\uC7A5",
                    "\uACE8\uBAA9", "\uB9C8\uC744", "\uBA85\uBB3C", "\uD2B9\uC0B0",
                    "\uB85C\uCEEC", "\uC21C\uB450\uBD80", "\uB9C9\uAD6D\uC218",
                    "\uAD6D\uBC25", "\uD55C\uC815\uC2DD");
            case ANY -> true;
        };
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase(java.util.Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private PlaceCandidate bestRequestedActivityCandidate(double currentLat,
                                                          double currentLng,
                                                          double targetLat,
                                                          double targetLng,
                                                          List<PlaceCandidate> available,
                                                          Map<PlaceCandidate, Double> adjustedScores,
                                                          List<String> pendingActivityCategories) {
        if (pendingActivityCategories.isEmpty()) return null;
        for (String categoryCode : pendingActivityCategories) {
            List<PlaceCandidate> matching = available.stream()
                    .filter(candidate -> categoryCode.equals(candidate.getCategoryCode()))
                    .toList();
            if (!matching.isEmpty()) {
                return bestOptionalCandidate(currentLat, currentLng, targetLat, targetLng, matching, adjustedScores);
            }
        }
        return null;
    }

    private double regenerateSlotSelectionScore(EditEntry entry,
                                                RegeneratePlan plan,
                                                double currentLat,
                                                double currentLng,
                                                double targetLat,
                                                double targetLng,
                                                Map<PlaceCandidate, Double> adjustedScores) {
        if (entry.getType() == EntryType.LUNCH || entry.getType() == EntryType.DINNER) {
            TravelLeg outbound = travel(currentLat, currentLng, plan.candidate().getLat(), plan.candidate().getLng());
            TravelLeg toTarget = travel(plan.candidate().getLat(), plan.candidate().getLng(), targetLat, targetLng);
            return mealRouteSelectionScore(plan.candidate(), outbound.minutes(), toTarget.minutes(), adjustedScores);
        }
        double currentDistanceKm = haversineKm(currentLat, currentLng,
                plan.candidate().getLat(), plan.candidate().getLng());
        return adjustedScores.getOrDefault(plan.candidate(), plan.candidate().getFinalScore())
                - currentDistanceKm * 0.7
                + randomJitter(ROUTE_RANDOM_SPREAD);
    }

    private double slotSelectionScore(RequiredSlot slot,
                                      SlotPlan plan,
                                      double currentLat,
                                      double currentLng,
                                      Map<PlaceCandidate, Double> adjustedScores) {
        if (fixedMealSlot(slot)) {
            return mealRouteSelectionScore(plan.candidate(), plan.outboundMinutes(), plan.toTargetMinutes(), adjustedScores);
        }
        double currentDistanceKm = haversineKm(currentLat, currentLng,
                plan.candidate().getLat(), plan.candidate().getLng());
        return adjustedScores.getOrDefault(plan.candidate(), plan.candidate().getFinalScore())
                - currentDistanceKm * 0.7
                + randomJitter(ROUTE_RANDOM_SPREAD);
    }

    private double mealRouteSelectionScore(PlaceCandidate candidate,
                                           int outboundMinutes,
                                           int toTargetMinutes,
                                           Map<PlaceCandidate, Double> adjustedScores) {
        int travelBurden = outboundMinutes + toTargetMinutes;
        double qualityTieBreaker = adjustedScores.getOrDefault(candidate, candidate.getFinalScore()) * 0.08;
        return -travelBurden * 4.0 + qualityTieBreaker + randomJitter(1.0);
    }

    private SlotPlan slotPlan(RequiredSlot slot,
                              PlaceCandidate candidate,
                              LocalTime currentTime,
                              double currentLat,
                              double currentLng,
                              double targetLat,
                              double targetLng,
                              LocalTime deadline) {
        TravelLeg outbound = travel(currentLat, currentLng, candidate.getLat(), candidate.getLng());
        LocalTime arrival = currentTime.plusMinutes(outbound.minutes);
        if (arrival.isBefore(slot.windowStart())) {
            arrival = slot.windowStart();
        }
        if (arrival.isAfter(slot.windowEnd())) {
            return null;
        }
        int stay = fixedMealSlot(slot) ? slot.minStayMinutes()
                : Math.max(slot.minStayMinutes(), candidate.getDefaultStayMinutes());
        if (arrival.plusMinutes(stay).isAfter(slot.windowEnd())) {
            return null;
        }
        int toTargetMinutes = 0;
        if (deadline != null) {
            TravelLeg toTarget = travel(candidate.getLat(), candidate.getLng(), targetLat, targetLng);
            toTargetMinutes = toTarget.minutes;
            if (arrival.plusMinutes(stay).plusMinutes(toTarget.minutes).isAfter(deadline)) {
                return null;
            }
        }
        return new SlotPlan(candidate, arrival, stay, outbound.minutes, toTargetMinutes);
    }

    private boolean fixedMealSlot(RequiredSlot slot) {
        return "lunch".equals(slot.id()) || "dinner".equals(slot.id());
    }

    private Set<String> reservedCategories(List<RequiredSlot> pendingSlots) {
        Set<String> categories = new LinkedHashSet<>();
        for (RequiredSlot slot : pendingSlots) {
            categories.add(slot.categoryCode());
        }
        return categories;
    }

    private String requiredSlotUnavailableMessage(RequiredSlot slot,
                                                  List<PlaceCandidate> remaining,
                                                  String currentName,
                                                  LocalTime currentTime,
                                                  LocalTime deadline) {
        long candidateCount = remaining.stream()
                .filter(candidate -> slot.categoryCode().equals(candidate.getCategoryCode()))
                .count();
        String deadlineText = deadline == null
                ? "없음"
                : deadline.toString();
        return slot.label() + " 시간대에 방문 가능한 장소가 없습니다. "
                + "허용 시간은 " + slot.windowStart() + "-" + slot.windowEnd()
                + "이고 최소 체류 시간은 " + slot.minStayMinutes() + "분입니다. "
                + "현재 일정 위치/시간은 " + currentName + " " + currentTime
                + ", 다음 고정 장소 또는 귀가 마감은 " + deadlineText
                + ", 남은 " + slot.label() + " 후보는 " + candidateCount + "개입니다. "
                + "주요 원인은 후보 장소 부족, 현재 위치에서 후보까지 이동 시간이 긴 경우, "
                + "도착 후 체류 시간이 허용 시간 안에 끝나지 않음, 또는 다음 일정/귀가 시간까지 이동할 시간이 부족한 경우입니다.";
    }

    private String regenerateSlotUnavailableMessage(EditEntry entry,
                                                    LocalTime currentTime,
                                                    int candidateCount) {
        TimeWindow window = regenerateWindow(entry);
        String windowText = window == null ? "별도 제한 없음" : window.start() + "-" + window.end();
        int stay = switch (entry.getType()) {
            case CAFE_SLOT -> 45;
            case LUNCH, DINNER -> 60;
            default -> 60;
        };
        return regenerateEntryLabel(entry) + " 허용 시간대에 방문 가능한 장소가 없어 건너뛰었습니다. "
                + "허용 시간은 " + windowText
                + ", 필요한 체류 시간은 " + stay + "분"
                + ", 현재 일정 시간은 " + currentTime
                + ", 후보는 " + candidateCount + "개입니다. "
                + "주요 원인은 후보까지 이동 후 허용 시간 안에 체류를 마칠 수 없거나 다음 일정까지 이동 시간이 부족한 경우입니다.";
    }

    private CourseResponse.CourseItem placeItem(String pid,
                                                String name,
                                                String cat,
                                                LocalTime arrival,
                                                int stay,
                                                int fee,
                                                String feeType,
                                                String note,
                                                String meal,
                                                double lat,
                                                double lng,
                                                int order,
                                                String role,
                                                String reason) {
        return CourseResponse.CourseItem.place(
                pid,
                name,
                cat,
                arrival.toString(),
                arrival.plusMinutes(stay).toString(),
                stay,
                fee,
                feeType,
                note,
                meal,
                lat,
                lng,
                order,
                role,
                reason
        );
    }

    private PlaceCandidate nearest(double lat, double lng, List<PlaceCandidate> candidates) {
        if (candidates.isEmpty()) {
            throw new java.util.NoSuchElementException();
        }
        return candidates.stream()
                .min(Comparator.comparingDouble(c -> haversineKm(lat, lng, c.getLat(), c.getLng())))
                .orElseThrow();
    }

    private PlaceCandidate bestOptionalCandidate(double currentLat,
                                                 double currentLng,
                                                 double targetLat,
                                                 double targetLng,
                                                 List<PlaceCandidate> candidates,
                                                 Map<PlaceCandidate, Double> adjustedScores) {
        if (candidates.isEmpty()) {
            throw new java.util.NoSuchElementException();
        }
        return candidates.stream()
                .max(Comparator.comparingDouble(candidate -> optionalSelectionScore(
                        candidate, currentLat, currentLng, targetLat, targetLng, adjustedScores)))
                .orElseThrow();
    }

    private double optionalSelectionScore(PlaceCandidate candidate,
                                          double currentLat,
                                          double currentLng,
                                          double targetLat,
                                          double targetLng,
                                          Map<PlaceCandidate, Double> adjustedScores) {
        double currentDistanceKm = haversineKm(currentLat, currentLng, candidate.getLat(), candidate.getLng());
        double targetDistanceKm = haversineKm(candidate.getLat(), candidate.getLng(), targetLat, targetLng);
        return adjustedScores.getOrDefault(candidate, candidate.getFinalScore())
                - currentDistanceKm * 0.7
                - targetDistanceKm * 0.3
                + randomJitter(ROUTE_RANDOM_SPREAD);
    }

    private TravelLeg travel(double fromLat, double fromLng, double toLat, double toLng) {
        int[] transit = odsayTransitService.getTransit(fromLat, fromLng, toLat, toLng);
        if (transit != null && transit.length >= 2) {
            int minutes = Math.max(1, (int) Math.ceil(transit[0] / 2.0));
            int fare = Math.max(0, (int) Math.ceil(transit[1] / 2.0));
            return new TravelLeg(fare > 0 ? "버스" : "도보", minutes, fare);
        }

        double km = haversineKm(fromLat, fromLng, toLat, toLng);
        if (km < 0.7) {
            return new TravelLeg("도보", Math.max(3, (int) Math.ceil(km / 4.0 * 60)), 0);
        }
        int minutes = Math.max(8, (int) Math.ceil(km / 18.0 * 60) + 5);
        return new TravelLeg("버스", minutes, 1_500);
    }

    private int placeCost(PlaceCandidate candidate, CourseRequest request) {
        return switch (candidate.getCategoryCode()) {
            case "FD6" -> mealCost(request);
            case "CE7" -> DEFAULT_CAFE_COST;
            default -> Math.max(0, candidate.getAdmissionFee());
        };
    }

    private int mealCost(CourseRequest request) {
        if (enabled(request.getLunch())) return mealEstimate(DEFAULT_LUNCH_COST, request.getLunch().getPriceLevel());
        if (enabled(request.getDinner())) return mealEstimate(DEFAULT_DINNER_COST, request.getDinner().getPriceLevel());
        return DEFAULT_LUNCH_COST;
    }

    private int requiredSlotCost(RequiredSlot slot, PlaceCandidate candidate, CourseRequest request) {
        if ("lunch".equals(slot.id()) && enabled(request.getLunch())) {
            return mealEstimate(DEFAULT_LUNCH_COST, request.getLunch().getPriceLevel());
        }
        if ("dinner".equals(slot.id()) && enabled(request.getDinner())) {
            return mealEstimate(DEFAULT_DINNER_COST, request.getDinner().getPriceLevel());
        }
        return placeCost(candidate, request);
    }

    private int mealEstimate(int baseCost, MealConfig.PriceLevel level) {
        return switch (level == null ? MealConfig.PriceLevel.MID : level) {
            case LOW -> Math.max(0, baseCost - MEAL_PRICE_STEP);
            case MID -> baseCost;
            case HIGH -> baseCost + MEAL_PRICE_STEP;
        };
    }

    private String slotRole(RequiredSlot slot) {
        if (slot.id().startsWith("cafe")) return "CAFE";
        return switch (slot.id()) {
            case "lunch" -> "LUNCH";
            case "dinner" -> "DINNER";
            default -> "OPTIONAL";
        };
    }

    private String categoryKey(String categoryCode) {
        return switch (categoryCode) {
            case "CT1" -> "culture";
            case "CE7" -> "cafe";
            case "FD6" -> "food";
            case "MT1" -> "shop";
            default -> "sight";
        };
    }

    private String feeType(PlaceCandidate candidate) {
        if (placeIsFoodOrCafe(candidate)) return "estimate";
        if (candidate.getAdmissionFee() == 0) return "free";
        return candidate.isFeeConfirmed() ? "confirm" : "estimate";
    }

    private String optionalPlaceNote(PlaceCandidate candidate, String targetName) {
        return String.format("점수 %.1f · %s", candidate.getFinalScore(),
                candidate.getAddress() != null ? candidate.getAddress() : "주소 정보 없음")
                + " · 다음 목표: " + targetName;
    }

    private boolean placeIsFoodOrCafe(PlaceCandidate candidate) {
        return "FD6".equals(candidate.getCategoryCode()) || "CE7".equals(candidate.getCategoryCode());
    }

    private boolean enabled(MealConfig meal) {
        return meal != null && meal.isEnabled();
    }

    private static int value(Integer value) {
        return value != null ? value : 0;
    }

    private void addTimelineWarnings(List<String> warnings,
                                     List<CourseResponse.CourseItem> items,
                                     LocalTime requestedReturnTime) {
        for (CourseResponse.CourseItem item : items) {
            if ("transit".equals(item.type()) && value(item.min()) >= 45) {
                warnings.add("긴 이동 구간이 있습니다: " + item.from() + " → " + item.to()
                        + " " + item.min() + "분");
            }
        }

        if (!items.isEmpty()) {
            LocalTime finalTime = items.stream()
                    .map(this::itemEndTime)
                    .max(Comparator.naturalOrder())
                    .orElse(requestedReturnTime);
            long spareMinutes = java.time.Duration.between(finalTime, requestedReturnTime).toMinutes();
            if (spareMinutes < 0) {
                warnings.add("코스가 귀가 시간을 " + Math.abs(spareMinutes)
                        + "분 초과합니다. 일부 일정을 빼거나 선호 순서를 줄여 주세요.");
            } else if (spareMinutes <= 30) {
                warnings.add("귀가 시간까지 여유가 " + spareMinutes + "분으로 촉박합니다.");
            }
        }

        for (CourseResponse.CourseItem item : items) {
            if (!"place".equals(item.type()) || item.role() == null) continue;
            LocalTime arr = parseTime(item.arr());
            LocalTime dep = parseTime(item.dep());
            if ("LUNCH".equals(item.role()) && (arr.equals(LUNCH_START) || dep.equals(LUNCH_END))) {
                warnings.add("점심 슬롯이 허용 시간 경계에 배치되었습니다.");
            } else if ("CAFE".equals(item.role()) && (arr.equals(CAFE_START) || dep.equals(CAFE_END))) {
                warnings.add("카페 슬롯이 허용 시간 경계에 배치되었습니다.");
            } else if ("DINNER".equals(item.role()) && (arr.equals(DINNER_START) || dep.equals(DINNER_END))) {
                warnings.add("저녁 슬롯이 허용 시간 경계에 배치되었습니다.");
            }
        }
    }

    private LocalTime itemEndTime(CourseResponse.CourseItem item) {
        if ("transit".equals(item.type()) && item.arr() != null) return parseTime(item.arr());
        if (item.dep() != null) return parseTime(item.dep());
        if (item.arr() != null) return parseTime(item.arr());
        return LocalTime.MIN;
    }

    private LocalTime parseTime(String time) {
        return LocalTime.parse(time);
    }

    private double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private double randomJitter(double spread) {
        if (spread <= 0) return 0;
        return ThreadLocalRandom.current().nextDouble(-spread, spread);
    }

    private record TravelLeg(String mode, int minutes, int fare) {}

    private record StartPoint(String name, double lat, double lng) {}

    private record SelectedTransport(String mode,
                                     String fromHub,
                                     String toHub,
                                     int oneWayMinutes,
                                     int mainMinutes,
                                     int originToHubMinutes,
                                     int originToHubFare,
                                     int fare) {}

    private record Anchor(String name, double lat, double lng, LocalTime visitTime, LocalTime departTime) {}

    private record ConcretePlace(String pid,
                                 String name,
                                 String cat,
                                 double lat,
                                 double lng,
                                 int stay,
                                 int fee,
                                 String feeType,
                                 String role,
                                 String meal) {}

    private record RankedCandidate(PlaceCandidate candidate, String key, double adjustedScore) {}

    private record RankingResult(List<PlaceCandidate> candidates, Map<PlaceCandidate, Double> adjustedScores) {}

    private record RequiredSlot(String id,
                                String label,
                                String categoryCode,
                                LocalTime windowStart,
                                LocalTime windowEnd,
                                int minStayMinutes,
                                String mealLabel,
                                MealConfig.FoodType foodType) {}

    private record SlotPlan(PlaceCandidate candidate,
                            LocalTime arrival,
                            int stay,
                            int outboundMinutes,
                            int toTargetMinutes) {}

    private record RegeneratePlan(PlaceCandidate candidate, LocalTime arrival, int stay) {}

    private record TimeWindow(LocalTime start, LocalTime end) {}

    private record BuildResult(List<CourseResponse.CourseItem> items,
                               List<String> warnings,
                               LocalTime endTime,
                               int placeCount) {}

    private record SegmentResult(String currentName,
                                 double currentLat,
                                 double currentLng,
                                 LocalTime currentTime,
                                 int placeCount) {}
}
