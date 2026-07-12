package com.gadang.mypage;

import com.gadang.user.UserSummaryResponse;

public record MypageSummaryResponse(
        UserSummaryResponse profile,
        long tripCount,
        long totalUsedCost,
        long postCount,
        long favoriteCount
) {
}
