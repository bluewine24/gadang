package com.gadang.course;

import com.gadang.algorithm.PlaceCandidate;
import com.gadang.algorithm.ScoredPlaceProvider;
import com.gadang.common.exception.GadangException;
import com.gadang.course.dto.CourseRequest;
import com.gadang.course.dto.CourseRegenerateRequest;
import com.gadang.course.dto.CourseResponse;
import com.gadang.external.odsay.OdsayTransitService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseRecommendationServiceTest {

    @Mock
    private ScoredPlaceProvider scoredPlaceProvider;

    @Mock
    private OdsayTransitService odsayTransitService;

    @InjectMocks
    private CourseRecommendationService service;

    @Test
    void callsProviderWithRegionHubAndAutoRadius() {
        CourseRequest request = baseRequest();
        request.setActivityTypes(List.of(CourseRequest.ActivityType.CULTURAL));
        request.setCafeEnabled(true);

        when(scoredPlaceProvider.getScoredPlaces(anyDouble(), anyDouble(), anyInt(), anyList(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(place("p1", "안목카페", "CE7", 37.7650, 128.9000, 90)));
        when(odsayTransitService.getTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(null);

        service.generate(request);

        ArgumentCaptor<Double> lat = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> lng = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Integer> radius = ArgumentCaptor.forClass(Integer.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> categories = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> regionHint = ArgumentCaptor.forClass(String.class);

        org.mockito.Mockito.verify(scoredPlaceProvider)
                .getScoredPlaces(lat.capture(), lng.capture(), radius.capture(), categories.capture(), regionHint.capture());

        assertThat(lat.getValue()).isEqualTo(37.7645);
        assertThat(lng.getValue()).isEqualTo(128.8996);
        assertThat(radius.getValue()).isEqualTo(15_000);
        assertThat(categories.getValue()).containsExactly("CT1", "CE7");
        assertThat(regionHint.getValue()).isEqualTo("강릉");
    }

    @Test
    void selectsTopKByScoreAndOrdersGreedilyByDistance() {
        CourseRequest request = baseRequest();
        when(scoredPlaceProvider.getScoredPlaces(anyDouble(), anyDouble(), anyInt(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(
                        place("far-high", "높은점수먼곳", "AT4", 37.9000, 129.1000, 100),
                        place("near-mid", "가까운중간", "AT4", 37.7650, 128.9000, 80),
                        place("low", "낮은점수", "AT4", 37.7660, 128.9010, 1)
                ));
        when(odsayTransitService.getTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(null);

        CourseResponse response = service.generate(request);

        List<String> placeNames = response.items().stream()
                .filter(item -> "place".equals(item.type()))
                .map(CourseResponse.CourseItem::name)
                .toList();

        assertThat(placeNames).startsWith("가까운중간");
        assertThat(placeNames).contains("높은점수먼곳");
    }

    @Test
    void cafeCandidateSurvivesBalancedTopKWhenCafeEnabled() {
        CourseRequest request = baseRequest();
        request.setCafeEnabled(true);
        List<PlaceCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            candidates.add(place("sight" + i, "상위관광지" + i, "AT4",
                    37.7700 + i * 0.001, 128.9000 + i * 0.001, 100 - i));
        }
        candidates.add(place("cafe-low", "살아남는카페", "CE7", 37.7650, 128.9000, 5));
        when(scoredPlaceProvider.getScoredPlaces(anyDouble(), anyDouble(), anyInt(), anyList(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(candidates);
        when(odsayTransitService.getTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(null);

        CourseResponse response = service.generate(request);

        assertThat(response.items().stream()
                .filter(item -> "place".equals(item.type()))
                .map(CourseResponse.CourseItem::name))
                .contains("살아남는카페");
    }

    @Test
    void foodCandidateSurvivesBalancedTopKWhenMealEnabled() {
        CourseRequest request = baseRequest();
        request.setLunch(meal(true));
        List<PlaceCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            candidates.add(place("sight" + i, "상위관광지" + i, "AT4",
                    37.7700 + i * 0.001, 128.9000 + i * 0.001, 100 - i));
        }
        candidates.add(place("food-low", "살아남는식당", "FD6", 37.7650, 128.9000, 5));
        when(scoredPlaceProvider.getScoredPlaces(anyDouble(), anyDouble(), anyInt(), anyList(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(candidates);
        when(odsayTransitService.getTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(null);

        CourseResponse response = service.generate(request);

        assertThat(response.items().stream()
                .filter(item -> "place".equals(item.type()))
                .map(CourseResponse.CourseItem::name))
                .contains("살아남는식당");
    }

    @Test
    void selectedLunchFoodTypePrefersMatchingRestaurant() {
        CourseRequest request = baseRequest();
        CourseRequest.MealConfig lunch = meal(true);
        lunch.setFoodType(CourseRequest.MealConfig.FoodType.JAPANESE);
        request.setLunch(lunch);
        when(scoredPlaceProvider.getScoredPlaces(anyDouble(), anyDouble(), anyInt(), anyList(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(
                        placeWithCategoryName("generic-food", "Popular Grill", "FD6", "\uC74C\uC2DD\uC810", 37.7650, 128.9000, 99),
                        placeWithCategoryName("sushi", "Sushi Spot", "FD6", "\uC77C\uC2DD > \uCD08\uBC25", 37.7660, 128.9010, 20)
                ));
        when(odsayTransitService.getTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(null);

        CourseResponse response = service.generate(request);

        assertThat(response.items().stream()
                .filter(item -> "LUNCH".equals(item.role()))
                .map(CourseResponse.CourseItem::name))
                .containsExactly("Sushi Spot");
    }

    @Test
    void requestedActivityCategoriesGetMinimumRepresentation() {
        CourseRequest request = baseRequest();
        request.setActivityTypes(List.of(CourseRequest.ActivityType.CULTURAL, CourseRequest.ActivityType.SHOPPING));
        List<PlaceCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            candidates.add(place("sight" + i, "상위관광지" + i, "AT4",
                    37.9000 + i * 0.001, 129.1000 + i * 0.001, 100 - i));
        }
        candidates.add(place("culture1", "가까운문화1", "CT1", 37.7646, 128.8997, 20));
        candidates.add(place("culture2", "가까운문화2", "CT1", 37.7647, 128.8998, 19));
        candidates.add(place("shop1", "가까운쇼핑1", "MT1", 37.7648, 128.8999, 18));
        candidates.add(place("shop2", "가까운쇼핑2", "MT1", 37.7649, 128.9000, 17));
        when(scoredPlaceProvider.getScoredPlaces(anyDouble(), anyDouble(), anyInt(), anyList(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(candidates);
        when(odsayTransitService.getTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(null);

        CourseResponse response = service.generate(request);

        List<String> names = response.items().stream()
                .filter(item -> "place".equals(item.type()))
                .map(CourseResponse.CourseItem::name)
                .toList();
        assertThat(names).contains("가까운문화1", "가까운쇼핑1");
    }

    @Test
    void repeatedActivityTypesCountAsSeparateRequestedPlaces() {
        CourseRequest request = baseRequest();
        request.setActivityTypes(List.of(
                CourseRequest.ActivityType.TOURIST_SPOT,
                CourseRequest.ActivityType.CULTURAL,
                CourseRequest.ActivityType.CULTURAL));
        List<PlaceCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            candidates.add(place("sight" + i, "상위관광지" + i, "AT4",
                    37.9000 + i * 0.001, 129.1000 + i * 0.001, 100 - i));
        }
        candidates.add(place("culture1", "반복문화1", "CT1", 37.7646, 128.8997, 20));
        candidates.add(place("culture2", "반복문화2", "CT1", 37.7647, 128.8998, 19));
        when(scoredPlaceProvider.getScoredPlaces(anyDouble(), anyDouble(), anyInt(), anyList(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(candidates);
        when(odsayTransitService.getTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(null);

        CourseResponse response = service.generate(request);

        List<String> names = response.items().stream()
                .filter(item -> "place".equals(item.type()))
                .map(CourseResponse.CourseItem::name)
                .toList();
        assertThat(names).contains("반복문화1", "반복문화2");
    }

    @Test
    void distancePenaltyLetsNearComparableCandidateSurviveTopK() {
        CourseRequest request = baseRequest();
        List<PlaceCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            candidates.add(place("far" + i, "먼약한후보" + i, "AT4",
                    38.2000 + i * 0.001, 129.3000 + i * 0.001, 80 - i));
        }
        candidates.add(place("near", "가까운비교후보", "AT4", 37.7520, 128.8762, 70));
        when(scoredPlaceProvider.getScoredPlaces(anyDouble(), anyDouble(), anyInt(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(candidates);
        when(odsayTransitService.getTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(null);

        CourseResponse response = service.generate(request);

        assertThat(response.items().stream()
                .filter(item -> "place".equals(item.type()))
                .map(CourseResponse.CourseItem::name))
                .contains("가까운비교후보");
    }

    @Test
    void highValueFarCandidateCanStillSurviveSoftDistancePenalty() {
        CourseRequest request = baseRequest();
        List<PlaceCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            candidates.add(place("near" + i, "가까운낮은후보" + i, "AT4",
                    37.7650 + i * 0.001, 128.9000 + i * 0.001, 30));
        }
        candidates.add(place("far-high", "먼고가치후보", "AT4", 38.0500, 129.1800, 180));
        when(scoredPlaceProvider.getScoredPlaces(anyDouble(), anyDouble(), anyInt(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(candidates);
        when(odsayTransitService.getTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(null);

        CourseResponse response = service.generate(request);

        assertThat(response.items().stream()
                .filter(item -> "place".equals(item.type()))
                .map(CourseResponse.CourseItem::name))
                .contains("먼고가치후보");
    }

    @Test
    void rejectsUnknownRegion() {
        CourseRequest request = baseRequest();
        request.setRegion("없는지역");

        assertThatThrownBy(() -> service.generate(request))
                .isInstanceOf(GadangException.class)
                .hasMessage("지원하지 않는 지역: 없는지역");
    }

    @Test
    void fixedPlaceAppearsAtExactVisitTime() {
        CourseRequest request = baseRequest();
        request.setFixedPlaces(List.of(fixed("안목해변", 37.7700, 128.9200, LocalTime.of(12, 0), LocalTime.of(13, 10))));
        when(scoredPlaceProvider.getScoredPlaces(anyDouble(), anyDouble(), anyInt(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(place("p1", "오죽헌", "CT1", 37.7650, 128.9000, 90)));
        when(odsayTransitService.getTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(null);

        CourseResponse response = service.generate(request);

        CourseResponse.CourseItem anchor = response.items().stream()
                .filter(item -> "place".equals(item.type()) && "안목해변".equals(item.name()))
                .findFirst()
                .orElseThrow();

        assertThat(anchor.arr()).isEqualTo("12:00");
        assertThat(anchor.dep()).isEqualTo("13:10");
        assertThat(anchor.stay()).isEqualTo(70);
        assertThat(anchor.role()).isEqualTo("ANCHOR");
        assertThat(anchor.reason()).isEqualTo("고정 장소");
        assertThat(anchor.note()).contains("필수 도착 12:00", "출발 13:10");
    }

    @Test
    void greedyPlacesBeforeAnchorPreserveAnchorArrivalTime() {
        CourseRequest request = baseRequest();
        request.setFixedPlaces(List.of(fixed("정동진", 37.6890, 129.0330, LocalTime.of(12, 0))));
        when(scoredPlaceProvider.getScoredPlaces(anyDouble(), anyDouble(), anyInt(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(
                        place("p1", "가까운장소", "AT4", 37.7650, 128.9000, 90),
                        place("p2", "먼장소", "AT4", 37.9000, 129.1000, 80)
                ));
        when(odsayTransitService.getTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(null);

        CourseResponse response = service.generate(request);
        List<CourseResponse.CourseItem> places = response.items().stream()
                .filter(item -> "place".equals(item.type()))
                .toList();

        assertThat(places.get(0).name()).isEqualTo("가까운장소");
        assertThat(places.stream()
                .filter(item -> "정동진".equals(item.name()))
                .findFirst()
                .orElseThrow()
                .arr()).isEqualTo("12:00");
    }

    @Test
    void placeSkippedBeforeAnchorCanStillBeUsedAfterAnchor() {
        CourseRequest request = baseRequest();
        request.setFixedPlaces(List.of(fixed("역앞고정", 37.7650, 128.9000, LocalTime.of(10, 30))));
        when(scoredPlaceProvider.getScoredPlaces(anyDouble(), anyDouble(), anyInt(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(place("p1", "먼후보", "AT4", 37.9000, 129.1000, 90)));
        when(odsayTransitService.getTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(null);

        CourseResponse response = service.generate(request);

        assertThat(response.items().stream()
                .filter(item -> "place".equals(item.type()))
                .map(CourseResponse.CourseItem::name))
                .contains("역앞고정", "먼후보");
    }

    @Test
    void multipleAnchorsAreSortedByVisitTime() {
        CourseRequest request = baseRequest();
        request.setFixedPlaces(List.of(
                fixed("두번째", 37.7800, 128.9100, LocalTime.of(14, 0)),
                fixed("첫번째", 37.7700, 128.9000, LocalTime.of(12, 0))
        ));
        when(scoredPlaceProvider.getScoredPlaces(anyDouble(), anyDouble(), anyInt(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        when(odsayTransitService.getTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(null);

        CourseResponse response = service.generate(request);

        List<String> anchorNames = response.items().stream()
                .filter(item -> "place".equals(item.type()))
                .map(CourseResponse.CourseItem::name)
                .toList();
        assertThat(anchorNames).containsExactly("첫번째", "두번째");
    }

    @Test
    void impossibleAnchorTimingReturnsBadRequest() {
        CourseRequest request = baseRequest();
        request.setFixedPlaces(List.of(fixed("먼고정장소", 37.9000, 129.1000, LocalTime.of(10, 5))));
        when(scoredPlaceProvider.getScoredPlaces(anyDouble(), anyDouble(), anyInt(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        when(odsayTransitService.getTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(null);

        assertThatThrownBy(() -> service.generate(request))
                .isInstanceOf(GadangException.class)
                .hasMessageContaining("고정 장소 '먼고정장소'에 10:05까지 도착할 수 없습니다.")
                .hasMessageContaining("이동 예상:")
                .hasMessageContaining("가장 빠른 도착:");
    }

    @Test
    void missingAnchorFieldsReturnBadRequest() {
        CourseRequest request = baseRequest();
        request.setFixedPlaces(List.of(new CourseRequest.FixedPlace()));

        assertThatThrownBy(() -> service.generate(request))
                .isInstanceOf(GadangException.class)
                .hasMessage("고정 장소는 placeName, lat, lng가 필요합니다.");
    }

    @Test
    void moreThanSixPinnedPlacesReturnsBadRequest() {
        CourseRequest request = baseRequest();
        List<CourseRequest.FixedPlace> fixedPlaces = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            fixedPlaces.add(pinned("고정" + i, 37.7650 + i * 0.001, 128.9000));
        }
        request.setFixedPlaces(fixedPlaces);

        assertThatThrownBy(() -> service.generate(request))
                .isInstanceOf(GadangException.class)
                .hasMessageContaining("최대 6개");
    }

    @Test
    void fixedPlaceWithoutTimesIsScheduledAsPinnedStop() {
        CourseRequest request = baseRequest();
        request.setFixedPlaces(List.of(pinned("황리단길", 37.7650, 128.9000)));
        when(scoredPlaceProvider.getScoredPlaces(anyDouble(), anyDouble(), anyInt(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        when(odsayTransitService.getTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(null);

        CourseResponse response = service.generate(request);

        CourseResponse.CourseItem pinned = response.items().stream()
                .filter(item -> "place".equals(item.type()))
                .filter(item -> "황리단길".equals(item.name()))
                .findFirst()
                .orElseThrow();
        assertThat(pinned.note()).isEqualTo("고정 장소");
        assertThat(pinned.arr()).isNotEqualTo("15:30");
    }

    @Test
    void orderedPreferenceEntriesPreservePinnedPlacement() {
        CourseRequest request = baseRequest();
        request.setPreferenceEntries(List.of(
                activitySlot(CourseRequest.ActivityType.CULTURAL),
                specificEntry("pin", "황리단길", 37.7650, 128.9000)
        ));
        when(scoredPlaceProvider.getScoredPlaces(anyDouble(), anyDouble(), anyInt(), anyList(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(place("culture", "새 전시관", "CT1", 37.7640, 128.8990, 95)));
        when(odsayTransitService.getTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(null);

        CourseResponse response = service.generate(request);

        List<String> places = response.items().stream()
                .filter(item -> "place".equals(item.type()))
                .map(CourseResponse.CourseItem::name)
                .toList();
        assertThat(places).containsSubsequence("새 전시관", "황리단길");
    }

    @Test
    void duplicateCandidateMatchingAnchorIsNotAddedTwice() {
        CourseRequest request = baseRequest();
        request.setFixedPlaces(List.of(fixed("오죽헌", 37.7650, 128.9000, LocalTime.of(12, 0))));
        when(scoredPlaceProvider.getScoredPlaces(anyDouble(), anyDouble(), anyInt(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(place("p1", "오죽헌", "CT1", 37.7650, 128.9000, 90)));
        when(odsayTransitService.getTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(null);

        CourseResponse response = service.generate(request);

        long count = response.items().stream()
                .filter(item -> "place".equals(item.type()) && "오죽헌".equals(item.name()))
                .count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void maxPlaceLimitIncludesAnchors() {
        CourseRequest request = baseRequest();
        request.setFixedPlaces(List.of(
                fixed("고정1", 37.7700, 128.9000, LocalTime.of(13, 0)),
                fixed("고정2", 37.7800, 128.9100, LocalTime.of(15, 0)),
                fixed("고정3", 37.7900, 128.9200, LocalTime.of(17, 0))
        ));
        when(scoredPlaceProvider.getScoredPlaces(anyDouble(), anyDouble(), anyInt(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(
                        place("p1", "장소1", "AT4", 37.7650, 128.9000, 90),
                        place("p2", "장소2", "AT4", 37.7660, 128.9010, 89),
                        place("p3", "장소3", "AT4", 37.7670, 128.9020, 88),
                        place("p4", "장소4", "AT4", 37.7680, 128.9030, 87),
                        place("p5", "장소5", "AT4", 37.7690, 128.9040, 86)
                ));
        when(odsayTransitService.getTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(null);

        CourseResponse response = service.generate(request);

        assertThat(response.placeCount()).isLessThanOrEqualTo(8);
        assertThat(response.items().stream().filter(item -> "place".equals(item.type())).count()).isLessThanOrEqualTo(8);
    }

    @Test
    void everyPlaceItemHasOrderDepartureRoleAndReason() {
        CourseRequest request = baseRequest();
        when(scoredPlaceProvider.getScoredPlaces(anyDouble(), anyDouble(), anyInt(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(
                        place("p1", "오죽헌", "CT1", 37.7650, 128.9000, 90),
                        place("p2", "경포해변", "AT4", 37.7660, 128.9010, 80)
                ));
        when(odsayTransitService.getTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(null);

        CourseResponse response = service.generate(request);

        List<CourseResponse.CourseItem> places = response.items().stream()
                .filter(item -> "place".equals(item.type()))
                .toList();
        assertThat(places).isNotEmpty();
        assertThat(places).allSatisfy(item -> {
            assertThat(item.order()).isNotNull();
            assertThat(item.dep()).isNotNull();
            assertThat(item.role()).isNotBlank();
            assertThat(item.reason()).isNotBlank();
        });
    }

    @Test
    void optionalPlaceOrderIncrementsAndExcludesTransit() {
        CourseRequest request = baseRequest();
        when(scoredPlaceProvider.getScoredPlaces(anyDouble(), anyDouble(), anyInt(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(
                        place("p1", "장소1", "AT4", 37.7650, 128.9000, 90),
                        place("p2", "장소2", "AT4", 37.7660, 128.9010, 80)
                ));
        when(odsayTransitService.getTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(null);

        CourseResponse response = service.generate(request);

        assertThat(response.items().stream()
                .filter(item -> "place".equals(item.type()))
                .map(CourseResponse.CourseItem::order))
                .containsExactly(1, 2);
        assertThat(response.items().stream()
                .filter(item -> "transit".equals(item.type()))
                .map(CourseResponse.CourseItem::order))
                .allMatch(order -> order == null);
    }

    @Test
    void lunchEnabledInsertsOneFoodPlaceInsideLunchWindow() {
        CourseRequest request = baseRequest();
        request.setLunch(meal(true));
        when(scoredPlaceProvider.getScoredPlaces(anyDouble(), anyDouble(), anyInt(), anyList(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(place("food1", "점심식당", "FD6", 37.7650, 128.9000, 90)));
        when(odsayTransitService.getTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(null);

        CourseResponse response = service.generate(request);

        List<CourseResponse.CourseItem> lunchItems = response.items().stream()
                .filter(item -> "place".equals(item.type()) && "점심".equals(item.meal()))
                .toList();
        assertThat(lunchItems).hasSize(1);
        assertThat(LocalTime.parse(lunchItems.get(0).arr())).isBetween(LocalTime.of(11, 0), LocalTime.of(14, 0));
        assertThat(lunchItems.get(0).stay()).isEqualTo(60);
        assertThat(lunchItems.get(0).fee()).isEqualTo(15_000);
        assertThat(lunchItems.get(0).role()).isEqualTo("LUNCH");
        assertThat(lunchItems.get(0).reason()).isEqualTo("필수 시간 슬롯");
        assertThat(lunchItems.get(0).note()).contains("점심 시간대", "11:00-14:00");
    }

    @Test
    void cafeEnabledInsertsOneCafePlaceInsideCafeWindow() {
        CourseRequest request = baseRequest();
        request.setCafeEnabled(true);
        when(scoredPlaceProvider.getScoredPlaces(anyDouble(), anyDouble(), anyInt(), anyList(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(place("cafe1", "안목카페", "CE7", 37.7650, 128.9000, 90)));
        when(odsayTransitService.getTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(null);

        CourseResponse response = service.generate(request);

        List<CourseResponse.CourseItem> cafeItems = response.items().stream()
                .filter(item -> "place".equals(item.type()) && "cafe".equals(item.cat()))
                .toList();
        assertThat(cafeItems).hasSize(1);
        assertThat(LocalTime.parse(cafeItems.get(0).arr())).isBetween(LocalTime.of(14, 0), LocalTime.of(17, 0));
        assertThat(cafeItems.get(0).role()).isEqualTo("CAFE");
        assertThat(cafeItems.get(0).reason()).isEqualTo("필수 시간 슬롯");
    }

    @Test
    void multipleCafeSlotsInsertMultipleCafePlaces() {
        CourseRequest request = baseRequest();
        request.setCafeSlots(List.of(
                cafeSlot(LocalTime.of(10, 0), LocalTime.of(12, 30)),
                cafeSlot(LocalTime.of(14, 0), LocalTime.of(17, 0))));
        when(scoredPlaceProvider.getScoredPlaces(anyDouble(), anyDouble(), anyInt(), anyList(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(
                        place("cafe1", "오전카페", "CE7", 37.7650, 128.9000, 90),
                        place("cafe2", "오후카페", "CE7", 37.7660, 128.9010, 88)));
        when(odsayTransitService.getTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(null);

        CourseResponse response = service.generate(request);

        List<CourseResponse.CourseItem> cafeItems = response.items().stream()
                .filter(item -> "place".equals(item.type()) && "cafe".equals(item.cat()))
                .toList();
        assertThat(cafeItems).hasSize(2);
        assertThat(cafeItems).allSatisfy(item -> assertThat(item.role()).isEqualTo("CAFE"));
        assertThat(cafeItems).extracting(CourseResponse.CourseItem::name)
                .containsExactlyInAnyOrder("오전카페", "오후카페");
    }

    @Test
    void dinnerEnabledInsertsOneFoodPlaceInsideDinnerWindow() {
        CourseRequest request = baseRequest();
        request.setDinner(meal(true));
        when(scoredPlaceProvider.getScoredPlaces(anyDouble(), anyDouble(), anyInt(), anyList(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(place("food1", "저녁식당", "FD6", 37.7650, 128.9000, 90)));
        when(odsayTransitService.getTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(null);

        CourseResponse response = service.generate(request);

        List<CourseResponse.CourseItem> dinnerItems = response.items().stream()
                .filter(item -> "place".equals(item.type()) && "저녁".equals(item.meal()))
                .toList();
        assertThat(dinnerItems).hasSize(1);
        assertThat(LocalTime.parse(dinnerItems.get(0).arr())).isBetween(LocalTime.of(17, 0), LocalTime.of(20, 0));
        assertThat(dinnerItems.get(0).stay()).isEqualTo(60);
        assertThat(dinnerItems.get(0).fee()).isEqualTo(20_000);
        assertThat(dinnerItems.get(0).role()).isEqualTo("DINNER");
        assertThat(dinnerItems.get(0).reason()).isEqualTo("필수 시간 슬롯");
    }

    @Test
    void mealSlotsUseExactlyOneHourEvenWhenCandidateStayIsLonger() {
        CourseRequest request = baseRequest();
        request.setLunch(meal(true));
        request.setDinner(meal(true));
        request.setReturnTime(LocalTime.of(21, 0));
        when(scoredPlaceProvider.getScoredPlaces(anyDouble(), anyDouble(), anyInt(), anyList(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(
                        placeWithStay("lunch", "점심식당", "FD6", 37.7650, 128.9000, 95, 90),
                        placeWithStay("dinner", "저녁식당", "FD6", 37.7670, 128.9020, 93, 90)
                ));
        when(odsayTransitService.getTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(null);

        CourseResponse response = service.generate(request);

        assertThat(response.items().stream()
                .filter(item -> "place".equals(item.type()) && ("LUNCH".equals(item.role()) || "DINNER".equals(item.role())))
                .map(CourseResponse.CourseItem::stay))
                .containsExactly(60, 60);
    }

    @Test
    void impossibleRequiredSlotReturnsBadRequest() {
        CourseRequest request = baseRequest();
        request.setLunch(meal(true));
        when(scoredPlaceProvider.getScoredPlaces(anyDouble(), anyDouble(), anyInt(), anyList(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(place("p1", "오죽헌", "CT1", 37.7650, 128.9000, 90)));

        assertThatThrownBy(() -> service.generate(request))
                .isInstanceOf(GadangException.class)
                .hasMessageContaining("점심 시간대에 방문 가능한 장소가 없습니다.")
                .hasMessageContaining("허용 시간은 11:00-14:00")
                .hasMessageContaining("최소 체류 시간은 60분")
                .hasMessageContaining("후보는 0개");
    }

    @Test
    void optionalGreedyDoesNotConsumeCandidateNeededByRequiredSlot() {
        CourseRequest request = baseRequest();
        request.setLunch(meal(true));
        when(scoredPlaceProvider.getScoredPlaces(anyDouble(), anyDouble(), anyInt(), anyList(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(
                        place("food1", "유일한식당", "FD6", 37.7650, 128.9000, 95),
                        place("sight1", "관광지", "AT4", 37.7660, 128.9010, 90)
                ));
        when(odsayTransitService.getTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(null);

        CourseResponse response = service.generate(request);

        CourseResponse.CourseItem food = response.items().stream()
                .filter(item -> "place".equals(item.type()) && "유일한식당".equals(item.name()))
                .findFirst()
                .orElseThrow();
        assertThat(food.meal()).isEqualTo("점심");
        assertThat(response.items().stream()
                .filter(item -> "place".equals(item.type()) && "유일한식당".equals(item.name()))
                .count()).isEqualTo(1);
    }

    @Test
    void mealSlotPrefersShortTravelOverHigherScore() {
        CourseRequest request = baseRequest();
        request.setLunch(meal(true));
        when(scoredPlaceProvider.getScoredPlaces(anyDouble(), anyDouble(), anyInt(), anyList(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(
                        place("far-food", "Far Lunch", "FD6", 37.7650, 129.1000, 100),
                        place("near-food", "Near Lunch", "FD6", 37.7650, 128.9000, 10)
                ));
        when(odsayTransitService.getTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(null);

        CourseResponse response = service.generate(request);

        assertThat(response.items().stream()
                .filter(item -> "LUNCH".equals(item.role()))
                .map(CourseResponse.CourseItem::name))
                .containsExactly("Near Lunch");
    }

    @Test
    void optionalGreedyLeavesFullMealStayInsideRequiredWindow() {
        CourseRequest request = baseRequest();
        request.setActivityTypes(List.of(CourseRequest.ActivityType.TOURIST_SPOT));
        request.setLunch(meal(true));
        when(scoredPlaceProvider.getScoredPlaces(anyDouble(), anyDouble(), anyInt(), anyList(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(
                        placeWithStay("far-sight", "Far Sight", "AT4", 37.7650, 129.1000, 100, 60),
                        place("near-food", "Near Lunch", "FD6", 37.7650, 128.9000, 80)
                ));
        when(odsayTransitService.getTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(null);

        CourseResponse response = service.generate(request);

        List<String> placeNames = response.items().stream()
                .filter(item -> "place".equals(item.type()))
                .map(CourseResponse.CourseItem::name)
                .toList();
        assertThat(placeNames.indexOf("Near Lunch")).isLessThan(placeNames.indexOf("Far Sight"));
    }

    @Test
    void anchorsAndRequiredSlotsCountTowardMaxPlaces() {
        CourseRequest request = baseRequest();
        request.setFixedPlaces(List.of(
                fixed("고정1", 37.7700, 128.9000, LocalTime.of(10, 40), LocalTime.of(11, 0)),
                fixed("고정2", 37.7800, 128.9100, LocalTime.of(13, 40), LocalTime.of(14, 0)),
                fixed("고정3", 37.7900, 128.9200, LocalTime.of(17, 0), LocalTime.of(17, 20))
        ));
        request.setLunch(meal(true));
        request.setCafeEnabled(true);
        request.setDinner(meal(true));
        when(scoredPlaceProvider.getScoredPlaces(anyDouble(), anyDouble(), anyInt(), anyList(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(
                        place("lunch", "점심식당", "FD6", 37.7650, 128.9000, 95),
                        place("cafe", "카페", "CE7", 37.7660, 128.9010, 94),
                        place("dinner", "저녁식당", "FD6", 37.7670, 128.9020, 93),
                        place("sight", "남는관광지", "AT4", 37.7680, 128.9030, 92)
                ));
        when(odsayTransitService.getTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(null);

        CourseResponse response = service.generate(request);

        assertThat(response.placeCount()).isEqualTo(6);
        assertThat(response.items().stream().filter(item -> "place".equals(item.type())).count()).isEqualTo(6);
    }

    @Test
    void requiredSlotsRespectAnchorDeadlines() {
        CourseRequest request = baseRequest();
        request.setFixedPlaces(List.of(fixed("정오앵커", 37.7700, 128.9000, LocalTime.of(12, 0), LocalTime.of(12, 30))));
        request.setLunch(meal(true));
        when(scoredPlaceProvider.getScoredPlaces(anyDouble(), anyDouble(), anyInt(), anyList(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(place("food1", "점심식당", "FD6", 37.7650, 128.9000, 90)));
        when(odsayTransitService.getTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(null);

        CourseResponse response = service.generate(request);

        CourseResponse.CourseItem lunch = response.items().stream()
                .filter(item -> "place".equals(item.type()) && "점심".equals(item.meal()))
                .findFirst()
                .orElseThrow();
        CourseResponse.CourseItem anchor = response.items().stream()
                .filter(item -> "place".equals(item.type()) && "정오앵커".equals(item.name()))
                .findFirst()
                .orElseThrow();
        assertThat(LocalTime.parse(lunch.arr())).isAfterOrEqualTo(LocalTime.of(12, 30));
        assertThat(anchor.arr()).isEqualTo("12:00");
    }

    @Test
    void tightTimeWindowSkipsPlaces() {
        CourseRequest request = baseRequest();
        request.setDepartureTime(LocalTime.of(10, 0));
        request.setReturnTime(LocalTime.of(10, 20));
        when(scoredPlaceProvider.getScoredPlaces(anyDouble(), anyDouble(), anyInt(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(place("p1", "오죽헌", "CT1", 37.7650, 128.9000, 90)));
        when(odsayTransitService.getTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(null);

        CourseResponse response = service.generate(request);

        assertThat(response.placeCount()).isZero();
        assertThat(response.warnings()).anyMatch(w -> w.contains("시간 안에 방문 가능한 장소"));
    }

    @Test
    void budgetOverrunProducesWarning() {
        CourseRequest request = baseRequest();
        request.setBudgetGuide(1_000);
        when(scoredPlaceProvider.getScoredPlaces(anyDouble(), anyDouble(), anyInt(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(place("p1", "오죽헌", "CT1", 37.7650, 128.9000, 90)));
        when(odsayTransitService.getTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(null);

        CourseResponse response = service.generate(request);

        assertThat(response.totalCost()).isGreaterThan(1_000);
        assertThat(response.warnings()).anyMatch(w -> w.contains("예상 비용이 예산 가이드"));
    }

    @Test
    void timelineItemsStayChronological() {
        CourseRequest request = baseRequest();
        request.setLunch(meal(true));
        when(scoredPlaceProvider.getScoredPlaces(anyDouble(), anyDouble(), anyInt(), anyList(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(
                        place("p1", "관광지", "AT4", 37.7650, 128.9000, 90),
                        place("food1", "점심식당", "FD6", 37.7660, 128.9010, 80)
                ));
        when(odsayTransitService.getTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(null);

        CourseResponse response = service.generate(request);

        LocalTime previous = LocalTime.MIN;
        for (CourseResponse.CourseItem item : response.items()) {
            LocalTime current = LocalTime.parse(item.arr());
            assertThat(current).isAfterOrEqualTo(previous);
            previous = item.dep() != null ? LocalTime.parse(item.dep()) : current;
        }
    }

    @Test
    void tightReturnWarningAppearsWhenFinalArrivalNearReturnTime() {
        CourseRequest request = baseRequest();
        request.setReturnTime(LocalTime.of(10, 40));
        when(scoredPlaceProvider.getScoredPlaces(anyDouble(), anyDouble(), anyInt(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(place("p1", "오죽헌", "CT1", 37.7650, 128.9000, 90)));
        when(odsayTransitService.getTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(null);

        CourseResponse response = service.generate(request);

        assertThat(response.warnings()).anyMatch(w -> w.contains("귀가 시간까지 여유"));
    }

    @Test
    void longTransferWarningAppearsForLongTransitLeg() {
        CourseRequest request = baseRequest();
        when(scoredPlaceProvider.getScoredPlaces(anyDouble(), anyDouble(), anyInt(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(place("p1", "먼장소", "AT4", 37.9000, 129.1000, 90)));
        when(odsayTransitService.getTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new int[]{90, 3_000});

        CourseResponse response = service.generate(request);

        assertThat(response.warnings()).anyMatch(w -> w.contains("긴 이동 구간"));
    }

    @Test
    void lowCandidateWarningStillAppears() {
        CourseRequest request = baseRequest();
        when(scoredPlaceProvider.getScoredPlaces(anyDouble(), anyDouble(), anyInt(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(place("p1", "오죽헌", "CT1", 37.7650, 128.9000, 90)));
        when(odsayTransitService.getTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(null);

        CourseResponse response = service.generate(request);

        assertThat(response.warnings()).anyMatch(w -> w.contains("장소 후보가 적어"));
    }

    @Test
    void emptyCandidateWithoutAnchorReturnsBadRequest() {
        CourseRequest request = baseRequest();
        when(scoredPlaceProvider.getScoredPlaces(anyDouble(), anyDouble(), anyInt(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.generate(request))
                .isInstanceOf(GadangException.class)
                .hasMessage("조건에 맞는 장소 후보가 없습니다. 목적지 지역이나 활동/카페/식사 조건을 조정해 주세요.");
    }

    @Test
    void regenerateKeepsLockedPlacesInOrderAndDeletedPlaceIsAbsent() {
        CourseRegenerateRequest request = regenerateRequest(List.of(
                lockedEntry("a", "첫 장소", 37.7650, 128.9000),
                lockedEntry("c", "셋째 장소", 37.7670, 128.9020)
        ));
        when(odsayTransitService.getTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(null);

        CourseResponse response = service.regenerate(request);

        List<String> places = response.items().stream()
                .filter(item -> "place".equals(item.type()))
                .map(CourseResponse.CourseItem::name)
                .toList();
        assertThat(places).containsExactly("첫 장소", "셋째 장소");
        assertThat(places).doesNotContain("삭제된 장소");
    }

    @Test
    void regenerateFillsActivitySlotBetweenLockedPlaces() {
        CourseRegenerateRequest request = regenerateRequest(List.of(
                lockedEntry("a", "첫 장소", 37.7650, 128.9000),
                activitySlot(CourseRequest.ActivityType.CULTURAL),
                lockedEntry("b", "마지막 장소", 37.7700, 128.9060)
        ));
        when(scoredPlaceProvider.getScoredPlaces(anyDouble(), anyDouble(), anyInt(), anyList(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(place("culture", "전시관", "CT1", 37.7670, 128.9020, 95)));
        when(odsayTransitService.getTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(null);

        CourseResponse response = service.regenerate(request);

        assertThat(response.items().stream()
                .filter(item -> "place".equals(item.type()))
                .map(CourseResponse.CourseItem::name))
                .containsExactly("첫 장소", "전시관", "마지막 장소");
    }

    @Test
    void regenerateActivitySlotExcludesOriginalPlace() {
        CourseRegenerateRequest.EditEntry slot = activitySlot(CourseRequest.ActivityType.CULTURAL);
        slot.setPid("old");
        slot.setPlaceName("Old Museum");
        slot.setLat(37.7650);
        slot.setLng(128.9000);
        CourseRegenerateRequest request = regenerateRequest(List.of(slot));
        when(scoredPlaceProvider.getScoredPlaces(anyDouble(), anyDouble(), anyInt(), anyList(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(
                        place("old", "Old Museum", "CT1", 37.7650, 128.9000, 100),
                        place("new", "New Museum", "CT1", 37.7660, 128.9010, 20)
                ));
        when(odsayTransitService.getTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(null);

        CourseResponse response = service.regenerate(request);

        assertThat(response.items().stream()
                .filter(item -> "place".equals(item.type()))
                .map(CourseResponse.CourseItem::name))
                .containsExactly("New Museum");
    }

    @Test
    void regenerateMealSlotPrefersSelectedFoodType() {
        CourseRegenerateRequest request = regenerateRequest(List.of(
                mealSlot(CourseRegenerateRequest.EntryType.LUNCH, CourseRequest.MealConfig.FoodType.SEAFOOD)
        ));
        when(scoredPlaceProvider.getScoredPlaces(anyDouble(), anyDouble(), anyInt(), anyList(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(
                        placeWithCategoryName("generic-food", "Popular Grill", "FD6", "\uC74C\uC2DD\uC810", 37.7650, 128.9000, 99),
                        placeWithCategoryName("seafood", "Seafood House", "FD6", "\uD574\uC0B0\uBB3C > \uD68C", 37.7660, 128.9010, 20)
                ));
        when(odsayTransitService.getTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(null);

        CourseResponse response = service.regenerate(request);

        assertThat(response.items().stream()
                .filter(item -> "LUNCH".equals(item.role()))
                .map(CourseResponse.CourseItem::name))
                .containsExactly("Seafood House");
    }

    @Test
    void regenerateMealSlotExcludesOriginalRestaurant() {
        CourseRegenerateRequest.EditEntry slot = mealSlot(
                CourseRegenerateRequest.EntryType.LUNCH,
                CourseRequest.MealConfig.FoodType.ANY);
        slot.setPid("old-food");
        slot.setPlaceName("Old Lunch");
        slot.setLat(37.7650);
        slot.setLng(128.9000);
        CourseRegenerateRequest request = regenerateRequest(List.of(slot));
        when(scoredPlaceProvider.getScoredPlaces(anyDouble(), anyDouble(), anyInt(), anyList(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(
                        place("old-food", "Old Lunch", "FD6", 37.7650, 128.9000, 100),
                        place("new-food", "New Lunch", "FD6", 37.7660, 128.9010, 20)
                ));
        when(odsayTransitService.getTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(null);

        CourseResponse response = service.regenerate(request);

        assertThat(response.items().stream()
                .filter(item -> "LUNCH".equals(item.role()))
                .map(CourseResponse.CourseItem::name))
                .containsExactly("New Lunch");
    }

    @Test
    void regeneratePreservesSpecificLocation() {
        CourseRegenerateRequest request = regenerateRequest(List.of(
                specificEntry("manual", "직접 추가 장소", 37.7650, 128.9000)
        ));
        when(odsayTransitService.getTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(null);

        CourseResponse response = service.regenerate(request);

        CourseResponse.CourseItem place = response.items().stream()
                .filter(item -> "place".equals(item.type()))
                .findFirst()
                .orElseThrow();
        assertThat(place.name()).isEqualTo("직접 추가 장소");
        assertThat(place.role()).isEqualTo("ANCHOR");
    }

    @Test
    void regenerateDoesNotReuseCandidateMatchingLockedPlace() {
        CourseRegenerateRequest request = regenerateRequest(List.of(
                lockedEntry("dup", "고정 장소", 37.7650, 128.9000),
                activitySlot(CourseRequest.ActivityType.CULTURAL)
        ));
        when(scoredPlaceProvider.getScoredPlaces(anyDouble(), anyDouble(), anyInt(), anyList(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(
                        place("dup", "고정 장소", "CT1", 37.7650, 128.9000, 100),
                        place("new", "새 전시관", "CT1", 37.7670, 128.9020, 90)
                ));
        when(odsayTransitService.getTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(null);

        CourseResponse response = service.regenerate(request);

        assertThat(response.items().stream()
                .filter(item -> "place".equals(item.type()))
                .map(CourseResponse.CourseItem::name))
                .containsExactly("고정 장소", "새 전시관");
    }

    @Test
    void regenerateOverReturnReturnsWarning() {
        CourseRegenerateRequest request = regenerateRequest(List.of(
                specificEntry("late", "늦은 장소", 37.9000, 129.1000)
        ));
        request.getBase().setReturnTime(LocalTime.of(10, 20));
        when(odsayTransitService.getTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new int[]{90, 3_000});

        CourseResponse response = service.regenerate(request);

        assertThat(LocalTime.parse(response.endTime())).isAfter(request.getBase().getReturnTime());
        assertThat(response.warnings()).anyMatch(w -> w.contains("초과"));
    }

    @Test
    void regenerateTooManyEntriesReturnsBadRequest() {
        List<CourseRegenerateRequest.EditEntry> entries = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            entries.add(lockedEntry("p" + i, "장소" + i, 37.765 + i * 0.001, 128.900));
        }

        assertThatThrownBy(() -> service.regenerate(regenerateRequest(entries)))
                .isInstanceOf(GadangException.class)
                .hasMessageContaining("최대 8개");
    }

    @Test
    void regenerateMissingSlotCandidateSkipsAndWarns() {
        CourseRegenerateRequest request = regenerateRequest(List.of(
                activitySlot(CourseRequest.ActivityType.CULTURAL)
        ));
        when(scoredPlaceProvider.getScoredPlaces(anyDouble(), anyDouble(), anyInt(), anyList(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());

        CourseResponse response = service.regenerate(request);

        assertThat(response.items().stream().filter(item -> "place".equals(item.type()))).isEmpty();
        assertThat(response.warnings()).anyMatch(w -> w.contains("후보 장소가 없어"));
    }

    @Test
    void requiredSlotCanReturnOverLimitWithWarning() {
        CourseRequest request = baseRequest();
        request.setReturnTime(LocalTime.of(17, 30));
        request.setDinner(meal(true));
        when(scoredPlaceProvider.getScoredPlaces(anyDouble(), anyDouble(), anyInt(), anyList(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(place("dinner", "저녁식당", "FD6", 37.7670, 128.9020, 93)));
        when(odsayTransitService.getTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(null);

        CourseResponse response = service.generate(request);

        assertThat(response.items()).anyMatch(item -> "DINNER".equals(item.role()));
        assertThat(LocalTime.parse(response.endTime())).isAfter(request.getReturnTime());
        assertThat(response.warnings()).anyMatch(w -> w.contains("초과"));
    }

    private CourseRegenerateRequest regenerateRequest(List<CourseRegenerateRequest.EditEntry> entries) {
        CourseRegenerateRequest request = new CourseRegenerateRequest();
        request.setBase(baseRequest());
        request.setEntries(entries);
        return request;
    }

    private CourseRegenerateRequest.EditEntry lockedEntry(String id, String name, double lat, double lng) {
        CourseRegenerateRequest.EditEntry entry = new CourseRegenerateRequest.EditEntry();
        entry.setClientId(id);
        entry.setType(CourseRegenerateRequest.EntryType.LOCKED_PLACE);
        entry.setPid(id);
        entry.setPlaceName(name);
        entry.setLat(lat);
        entry.setLng(lng);
        entry.setCat("sight");
        entry.setRole("LOCKED");
        entry.setStayMinutes(45);
        entry.setFee(0);
        entry.setFeeType("free");
        return entry;
    }

    private CourseRegenerateRequest.EditEntry specificEntry(String id, String name, double lat, double lng) {
        CourseRegenerateRequest.EditEntry entry = lockedEntry(id, name, lat, lng);
        entry.setType(CourseRegenerateRequest.EntryType.SPECIFIC_PLACE);
        entry.setRole("ANCHOR");
        return entry;
    }

    private CourseRegenerateRequest.EditEntry activitySlot(CourseRequest.ActivityType type) {
        CourseRegenerateRequest.EditEntry entry = new CourseRegenerateRequest.EditEntry();
        entry.setClientId("slot-" + type);
        entry.setType(CourseRegenerateRequest.EntryType.ACTIVITY_SLOT);
        entry.setActivityType(type);
        return entry;
    }

    private CourseRegenerateRequest.EditEntry mealSlot(CourseRegenerateRequest.EntryType type,
                                                       CourseRequest.MealConfig.FoodType foodType) {
        CourseRegenerateRequest.EditEntry entry = new CourseRegenerateRequest.EditEntry();
        entry.setClientId("slot-" + type);
        entry.setType(type);
        entry.setFoodType(foodType);
        entry.setPriceLevel(CourseRequest.MealConfig.PriceLevel.MID);
        return entry;
    }

    private CourseRequest baseRequest() {
        CourseRequest request = new CourseRequest();
        request.setRegion("강릉");
        request.setDepartureTime(LocalTime.of(10, 0));
        request.setReturnTime(LocalTime.of(19, 0));
        return request;
    }

    private CourseRequest.FixedPlace fixed(String name, double lat, double lng, LocalTime visitTime) {
        return fixed(name, lat, lng, visitTime, visitTime.plusMinutes(45));
    }

    private CourseRequest.FixedPlace pinned(String name, double lat, double lng) {
        CourseRequest.FixedPlace fixed = new CourseRequest.FixedPlace();
        fixed.setPlaceName(name);
        fixed.setLat(lat);
        fixed.setLng(lng);
        return fixed;
    }

    private CourseRequest.FixedPlace fixed(String name, double lat, double lng,
                                           LocalTime visitTime, LocalTime departTime) {
        CourseRequest.FixedPlace fixed = new CourseRequest.FixedPlace();
        fixed.setPlaceName(name);
        fixed.setLat(lat);
        fixed.setLng(lng);
        fixed.setVisitTime(visitTime);
        fixed.setDepartTime(departTime);
        return fixed;
    }

    private CourseRequest.MealConfig meal(boolean enabled) {
        CourseRequest.MealConfig meal = new CourseRequest.MealConfig();
        meal.setEnabled(enabled);
        meal.setFoodType(CourseRequest.MealConfig.FoodType.ANY);
        meal.setPriceLevel(CourseRequest.MealConfig.PriceLevel.MID);
        return meal;
    }

    private CourseRequest.CafeConfig cafeSlot(LocalTime start, LocalTime end) {
        CourseRequest.CafeConfig cafe = new CourseRequest.CafeConfig();
        cafe.setCafeType(CourseRequest.CafeType.GENERAL);
        cafe.setStartTime(start);
        cafe.setEndTime(end);
        return cafe;
    }

    private PlaceCandidate place(String id, String name, String category,
                                 double lat, double lng, double score) {
        return placeWithStay(id, name, category, lat, lng, score, 30);
    }

    private PlaceCandidate placeWithCategoryName(String id, String name, String category, String categoryName,
                                                 double lat, double lng, double score) {
        PlaceCandidate candidate = placeWithStay(id, name, category, lat, lng, score, 30);
        candidate.setCategoryName(categoryName);
        return candidate;
    }

    private PlaceCandidate placeWithStay(String id, String name, String category,
                                         double lat, double lng, double score, int stayMinutes) {
        return PlaceCandidate.builder()
                .kakaoPlaceId(id)
                .name(name)
                .categoryCode(category)
                .categoryName(category)
                .lat(lat)
                .lng(lng)
                .address("주소")
                .distanceMeters(100)
                .finalScore(score)
                .admissionFee("CT1".equals(category) ? 5_000 : 0)
                .feeConfirmed(false)
                .defaultStayMinutes(stayMinutes)
                .build();
    }
}
