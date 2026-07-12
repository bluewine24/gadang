package com.gadang.admin;

public record OperationSummaryResponse(
        long userCount,
        long tripCount,
        long postCount,
        long noticeCount,
        long placeCount
) {
}
