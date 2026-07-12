package com.gadang.external.tour.dto;

public record TourDetailDto(
        String img,
        String overview,
        // 운영 정보
        String usetime,
        String usefee,
        String restdate,
        String parking,
        // 편의 정보
        String homepage,
        String infocenter,
        String pet,
        String babycarriage,
        String creditcard,
        // 카테고리 특화
        String menu,        // 음식점: 대표메뉴
        String seat,        // 음식점: 좌석수
        String useseason,   // 관광지: 이용시기
        String expguide,    // 관광지: 체험안내
        String scale        // 문화시설/쇼핑: 규모
) {}
