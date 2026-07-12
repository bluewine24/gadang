package com.gadang.course.dto;

import java.util.List;

public record CourseResponse(
        String title,
        String region,
        String startPoint,
        String startTime,
        String endTime,
        int totalMin,
        int placeCount,
        int totalCost,
        TransportSelection selectedTransport,
        List<CourseItem> items,
        List<String> warnings
) {

    public record TransportSelection(
            String mode,
            String fromHub,
            String toHub,
            Integer oneWayMin,
            Integer fare,
            Integer originToHubMin,
            Integer originToHubFare
    ) {}

    public record CourseItem(
            String type,
            String mode,
            String from,
            String to,
            String dep,
            String arr,
            Integer min,
            Integer fare,
            String feeType,
            String pid,
            String name,
            String cat,
            Integer stay,
            Integer fee,
            String note,
            String meal,
            Double lat,
            Double lng,
            Integer order,
            String role,
            String reason
    ) {
        public static CourseItem transit(String mode, String from, String to,
                                         String dep, String arr, int min, int fare) {
            return new CourseItem("transit", mode, from, to, dep, arr, min, fare,
                    fare > 0 ? "estimate" : "free",
                    null, null, null, null, null, null, null, null, null,
                    null, null, null);
        }

        public static CourseItem place(String pid, String name, String cat,
                                       String arr, String dep, int stay, int fee, String feeType,
                                       String note, String meal, double lat, double lng,
                                       int order, String role, String reason) {
            return new CourseItem("place", null, null, null, dep, arr, null, null,
                    feeType, pid, name, cat, stay, fee, note, meal, lat, lng,
                    order, role, reason);
        }
    }
}
