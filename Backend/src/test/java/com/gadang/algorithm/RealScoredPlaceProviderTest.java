package com.gadang.algorithm;

import com.gadang.external.transit.ExternalCacheMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RealScoredPlaceProviderTest {

    private final PlaceFilterService placeFilterService = mock(PlaceFilterService.class);
    private final ExternalCacheMapper cacheMapper = mock(ExternalCacheMapper.class);
    private final RealScoredPlaceProvider provider = new RealScoredPlaceProvider(placeFilterService, cacheMapper);

    @Test
    void emptyDbCacheIsIgnoredAndRebuilt() {
        PlaceCandidate candidate = PlaceCandidate.builder()
                .name("부산타워")
                .categoryCode("AT4")
                .finalScore(80)
                .build();
        when(cacheMapper.findPlaces(anyString(), any(LocalDateTime.class))).thenReturn("[]");
        when(placeFilterService.filterByCoord(anyDouble(), anyDouble(), anyInt(), any(), eq("부산")))
                .thenReturn(List.of(candidate));

        List<PlaceCandidate> result = provider.getScoredPlaces(35.1152, 129.0415, 20_000, List.of("AT4"), "부산");

        assertThat(result).containsExactly(candidate);
        verify(placeFilterService).filterByCoord(anyDouble(), anyDouble(), anyInt(), any(), eq("부산"));
        verify(cacheMapper).upsertPlaces(anyString(), anyString());
    }

    @Test
    void emptyLiveResultIsNotSavedToDbCache() {
        when(cacheMapper.findPlaces(anyString(), any(LocalDateTime.class))).thenReturn(null);
        when(placeFilterService.filterByCoord(anyDouble(), anyDouble(), anyInt(), any(), eq("부산")))
                .thenReturn(List.of());

        List<PlaceCandidate> result = provider.getScoredPlaces(35.1152, 129.0415, 20_000, List.of("AT4"), "부산");

        assertThat(result).isEmpty();
        verify(cacheMapper, never()).upsertPlaces(anyString(), anyString());
    }
}
