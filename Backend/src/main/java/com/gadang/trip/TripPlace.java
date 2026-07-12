package com.gadang.trip;

import lombok.Data;

@Data
public class TripPlace {
    private Long placeId;
    private String kakaoPlaceId;
    private String name;
    private String categoryCode;
    private String categoryName;
    private String address;
    private double lat;
    private double lng;
}
