package com.gadang.trip;

import com.gadang.common.exception.GadangException;
import com.gadang.course.dto.CourseResponse;
import com.gadang.mypage.TripSummaryResponse;
import com.gadang.trip.TripDtos.TripDetailResponse;
import com.gadang.trip.TripDtos.TripSaveRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripServiceTest {

    @Mock
    private TripMapper tripMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void saveStoresPlanItemsRoutesAndReturnsTripId() {
        TripService service = new TripService(tripMapper, objectMapper);
        stubGeneratedIds(true);
        when(tripMapper.findPlaceIdByKakaoPlaceId("place-1")).thenReturn(100L);
        when(tripMapper.findPlaceIdByKakaoPlaceId(org.mockito.ArgumentMatchers.startsWith("COURSE:"))).thenReturn(null);

        Long tripId = service.save(7L, new TripSaveRequest("내 경주 코스", LocalDate.of(2026, 6, 23), sampleCourse()));

        assertThat(tripId).isEqualTo(77L);

        ArgumentCaptor<TripPlan> planCaptor = ArgumentCaptor.forClass(TripPlan.class);
        verify(tripMapper).insert(planCaptor.capture());
        TripPlan plan = planCaptor.getValue();
        assertThat(plan.getUserId()).isEqualTo(7L);
        assertThat(plan.getTitle()).isEqualTo("내 경주 코스");
        assertThat(plan.getTripDate()).isEqualTo(LocalDate.of(2026, 6, 23));
        assertThat(plan.getStartPoint()).isEqualTo("신경주역");
        assertThat(plan.getDepartureTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(plan.getReturnTime()).isEqualTo(LocalTime.of(19, 0));
        assertThat(plan.getTotalCost()).isEqualTo(35_000);
        assertThat(plan.getFoodCostEst()).isEqualTo(15_000);
        assertThat(plan.getCourseJson()).contains("황리단길");
        assertThat(plan.getCourseJson()).contains("selectedTransport", "KTX", "서울역", "신경주역");

        ArgumentCaptor<TripPlace> placeCaptor = ArgumentCaptor.forClass(TripPlace.class);
        verify(tripMapper).insertPlace(placeCaptor.capture());
        assertThat(placeCaptor.getValue().getKakaoPlaceId()).startsWith("COURSE:");
        assertThat(placeCaptor.getValue().getCategoryCode()).isEqualTo("FD6");

        ArgumentCaptor<TripItemRow> itemCaptor = ArgumentCaptor.forClass(TripItemRow.class);
        verify(tripMapper, org.mockito.Mockito.times(2)).insertItem(itemCaptor.capture());
        assertThat(itemCaptor.getAllValues()).extracting(TripItemRow::getTripId).containsOnly(77L);
        assertThat(itemCaptor.getAllValues()).extracting(TripItemRow::getPlaceId).containsExactly(100L, 201L);
        assertThat(itemCaptor.getAllValues()).extracting(TripItemRow::getVisitOrder).containsExactly(1, 2);
        assertThat(itemCaptor.getAllValues().get(0).getAdmissionFee()).isEqualTo(5_000);
        assertThat(itemCaptor.getAllValues().get(1).getFoodCost()).isEqualTo(15_000);

        ArgumentCaptor<TripRouteRow> routeCaptor = ArgumentCaptor.forClass(TripRouteRow.class);
        verify(tripMapper, org.mockito.Mockito.times(3)).insertRoute(routeCaptor.capture());
        assertThat(routeCaptor.getAllValues()).extracting(TripRouteRow::getTripId).containsOnly(77L);
        assertThat(routeCaptor.getAllValues()).extracting(TripRouteRow::getTransportType)
                .containsExactly("BUS", "WALK", "BUS");
        assertThat(routeCaptor.getAllValues().get(0).getFromItemId()).isNull();
        assertThat(routeCaptor.getAllValues().get(0).getToItemId()).isEqualTo(301L);
        assertThat(routeCaptor.getAllValues().get(2).getFromItemId()).isEqualTo(302L);
        assertThat(routeCaptor.getAllValues().get(2).getToItemId()).isEqualTo(302L);
    }

    @Test
    void saveDefaultsTripDateAndTitle() {
        TripService service = new TripService(tripMapper, objectMapper);
        stubGeneratedIds(false);
        when(tripMapper.findPlaceIdByKakaoPlaceId(any())).thenReturn(100L);

        service.save(7L, new TripSaveRequest(null, null, sampleCourse()));

        ArgumentCaptor<TripPlan> planCaptor = ArgumentCaptor.forClass(TripPlan.class);
        verify(tripMapper).insert(planCaptor.capture());
        assertThat(planCaptor.getValue().getTitle()).isEqualTo("경주 당일치기");
        assertThat(planCaptor.getValue().getTripDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void saveRejectsNullCourse() {
        TripService service = new TripService(tripMapper, objectMapper);

        assertThatThrownBy(() -> service.save(7L, new TripSaveRequest("빈 코스", null, null)))
                .isInstanceOf(GadangException.class)
                .hasMessage("저장할 코스 정보가 필요합니다.");

        verify(tripMapper, never()).insert(any());
    }

    @Test
    void saveRejectsCourseWithoutPlaces() {
        TripService service = new TripService(tripMapper, objectMapper);
        CourseResponse empty = new CourseResponse("빈 코스", "경주", "신경주역", "10:00", "10:10",
                10, 0, 0,
                null,
                List.of(CourseResponse.CourseItem.transit("버스", "A", "B", "10:00", "10:10", 10, 1500)),
                List.of());

        assertThatThrownBy(() -> service.save(7L, new TripSaveRequest("빈 코스", null, empty)))
                .isInstanceOf(GadangException.class)
                .hasMessage("저장할 방문 장소가 필요합니다.");

        verify(tripMapper, never()).insert(any());
    }

    @Test
    void getRejectsAnotherUsersTrip() {
        TripService service = new TripService(tripMapper, objectMapper);
        TripPlan plan = new TripPlan();
        plan.setTripId(77L);
        plan.setUserId(8L);
        when(tripMapper.findById(77L)).thenReturn(plan);

        assertThatThrownBy(() -> service.get(7L, 77L))
                .isInstanceOf(GadangException.class)
                .hasMessage("권한이 없습니다.");
    }

    @Test
    void getReturnsSnapshotCourse() {
        TripService service = new TripService(tripMapper, objectMapper);
        CourseResponse course = sampleCourse();
        TripPlan plan = new TripPlan();
        plan.setTripId(77L);
        plan.setUserId(7L);
        plan.setTitle("저장된 코스");
        plan.setTripDate(LocalDate.of(2026, 6, 23));
        plan.setStartPoint("신경주역");
        plan.setEndPoint("신경주역");
        plan.setDepartureTime(LocalTime.of(10, 0));
        plan.setReturnTime(LocalTime.of(19, 0));
        plan.setTotalCost(35_000);
        plan.setFoodCostEst(15_000);
        plan.setCourseJson(objectMapper.writeValueAsString(course));
        when(tripMapper.findById(77L)).thenReturn(plan);

        TripDetailResponse response = service.get(7L, 77L);

        assertThat(response.tripId()).isEqualTo(77L);
        assertThat(response.course().title()).isEqualTo("경주 추천 코스");
        assertThat(response.course().items()).hasSize(5);
    }

    private void stubGeneratedIds(boolean includePlaceInsert) {
        doAnswer(invocation -> {
            TripPlan plan = invocation.getArgument(0);
            plan.setTripId(77L);
            return null;
        }).when(tripMapper).insert(any(TripPlan.class));
        if (includePlaceInsert) {
            doAnswer(invocation -> {
                TripPlace place = invocation.getArgument(0);
                place.setPlaceId(201L);
                return null;
            }).when(tripMapper).insertPlace(any(TripPlace.class));
        }
        final long[] itemId = {300L};
        doAnswer(invocation -> {
            TripItemRow row = invocation.getArgument(0);
            row.setItemId(++itemId[0]);
            return null;
        }).when(tripMapper).insertItem(any(TripItemRow.class));
    }

    private CourseResponse sampleCourse() {
        return new CourseResponse(
                "경주 추천 코스",
                "경주",
                "신경주역",
                "10:00",
                "19:00",
                240,
                2,
                35_000,
                new CourseResponse.TransportSelection("KTX", "서울역", "신경주역", 120, 25000, 15, 0),
                List.of(
                        CourseResponse.CourseItem.transit("버스", "신경주역", "황리단길", "10:00", "10:30", 30, 1500),
                        CourseResponse.CourseItem.place("place-1", "황리단길", "culture",
                                "10:30", "11:30", 60, 5_000, "estimate",
                                "점수 90.0 · 경북 경주시", null, 35.84, 129.21, 1, "OPTIONAL", "동선 최적화 선택"),
                        CourseResponse.CourseItem.transit("도보", "황리단길", "점심식당", "11:30", "11:40", 10, 0),
                        CourseResponse.CourseItem.place(null, "점심식당", "food",
                                "11:40", "12:35", 55, 15_000, "estimate",
                                "점심 시간대 11:30-13:30", "점심", 35.841, 129.211, 2, "LUNCH", "필수 시간 슬롯"),
                        CourseResponse.CourseItem.transit("버스", "점심식당", "신경주역", "12:35", "13:05", 30, 1500)
                ),
                List.of()
        );
    }
}
