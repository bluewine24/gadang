package com.gadang.external.region;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gadang.external.region.dto.DailyForecastDto;
import com.gadang.external.region.dto.WeatherDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherService {

    @Qualifier("openMeteoRestClient")
    private final RestClient openMeteoRestClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 주요 지역 중심 좌표 (lat, lng)
    private static final Map<String, double[]> REGION_COORD = Map.ofEntries(
            Map.entry("강릉", new double[]{37.793, 128.919}),
            Map.entry("인천", new double[]{37.474, 126.618}),
            Map.entry("수원", new double[]{37.289, 127.015}),
            Map.entry("춘천", new double[]{37.876, 127.734}),
            Map.entry("전주", new double[]{35.819, 127.153}),
            Map.entry("아산", new double[]{36.790, 127.003}),
            Map.entry("서울", new double[]{37.567, 126.978}),
            Map.entry("부산", new double[]{35.180, 129.075}),
            Map.entry("대구", new double[]{35.870, 128.601}),
            Map.entry("광주", new double[]{35.160, 126.851}),
            Map.entry("대전", new double[]{36.351, 127.385}),
            Map.entry("울산", new double[]{35.538, 129.311}),
            Map.entry("제주", new double[]{33.489, 126.498}),
            Map.entry("경주", new double[]{35.856, 129.225}),
            Map.entry("여수", new double[]{34.761, 127.662}),
            Map.entry("포항", new double[]{36.019, 129.343})
    );

    @Cacheable(value = "weather", key = "#region + '-' + #lat + '-' + #lng")
    public Optional<WeatherDto> fetchWeather(String region, Double lat, Double lng) {
        try {
            double[] coord = resolveCoord(region, lat, lng);
            if (coord == null) return Optional.empty();

            String body = openMeteoRestClient.get()
                    .uri(u -> u.path("/forecast")
                            .queryParam("latitude", coord[0])
                            .queryParam("longitude", coord[1])
                            .queryParam("current", "temperature_2m,apparent_temperature,precipitation_probability,weather_code,wind_speed_10m,relative_humidity_2m")
                            .queryParam("daily", "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max")
                            .queryParam("timezone", "Asia/Seoul")
                            .queryParam("forecast_days", 7)
                            .build())
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(body);
            JsonNode cur = root.path("current");

            int code = cur.path("weather_code").asInt(0);
            double temp = cur.path("temperature_2m").asDouble();
            double feels = cur.path("apparent_temperature").asDouble();
            int humidity = cur.path("relative_humidity_2m").asInt();
            double wind = cur.path("wind_speed_10m").asDouble();
            int precipProb = cur.path("precipitation_probability").asInt();

            return Optional.of(new WeatherDto(
                    weatherEmoji(code),
                    weatherDesc(code),
                    String.format("%.0f°C", temp),
                    String.format("%.0f°C", feels),
                    humidity + "%",
                    String.format("%.1fm/s", wind / 3.6),  // km/h → m/s
                    precipProb,
                    parseDailyForecast(root.path("daily"))
            ));
        } catch (Exception e) {
            log.warn("[Weather] fetchWeather failed for '{}': {}", region, e.getMessage());
            return Optional.empty();
        }
    }

    private static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("MM-dd");
    private static final String[] DOW_KR = {"월", "화", "수", "목", "금", "토", "일"};

    /** Open-Meteo daily 블록(병렬 배열) → 요일별 DTO 리스트로 변환 */
    private List<DailyForecastDto> parseDailyForecast(JsonNode daily) {
        List<DailyForecastDto> out = new ArrayList<>();
        JsonNode dates = daily.path("time");
        if (!dates.isArray()) return out;

        for (int i = 0; i < dates.size(); i++) {
            LocalDate date = LocalDate.parse(dates.get(i).asText());
            int code = daily.path("weather_code").path(i).asInt(0);
            double max = daily.path("temperature_2m_max").path(i).asDouble();
            double min = daily.path("temperature_2m_min").path(i).asDouble();
            int precip = daily.path("precipitation_probability_max").path(i).asInt(0);

            out.add(new DailyForecastDto(
                    date.format(MD),
                    DOW_KR[date.getDayOfWeek().getValue() - 1],
                    weatherEmoji(code),
                    String.format("%.0f°", max),
                    String.format("%.0f°", min),
                    precip
            ));
        }
        return out;
    }

    private double[] resolveCoord(String region, Double lat, Double lng) {
        if (lat != null && lng != null) return new double[]{lat, lng};
        if (region != null) {
            for (Map.Entry<String, double[]> e : REGION_COORD.entrySet()) {
                if (region.contains(e.getKey())) return e.getValue();
            }
        }
        return null;
    }

    private String weatherEmoji(int code) {
        if (code == 0)          return "☀️";
        if (code <= 3)          return "⛅";
        if (code <= 48)         return "🌫️";
        if (code <= 67)         return "🌧️";
        if (code <= 77)         return "🌨️";
        if (code <= 82)         return "🌦️";
        if (code <= 99)         return "⛈️";
        return "☁️";
    }

    private String weatherDesc(int code) {
        if (code == 0)          return "맑음";
        if (code <= 3)          return "구름조금";
        if (code <= 48)         return "안개";
        if (code <= 55)         return "이슬비";
        if (code <= 67)         return "비";
        if (code <= 77)         return "눈";
        if (code <= 82)         return "소나기";
        if (code <= 99)         return "뇌우";
        return "흐림";
    }
}
