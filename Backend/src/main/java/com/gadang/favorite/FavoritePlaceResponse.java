package com.gadang.favorite;

import java.time.LocalDateTime;

public record FavoritePlaceResponse(
        Long favoriteId,
        Long placeId,
        String name,
        String categoryCode,
        String categoryName,
        String address,
        Double lat,
        Double lng,
        String placeUrl,
        LocalDateTime createdAt
) {
}
