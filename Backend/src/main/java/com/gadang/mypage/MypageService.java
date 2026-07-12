package com.gadang.mypage;

import com.gadang.common.response.PageResponse;
import com.gadang.community.CommunityService;
import com.gadang.community.PostResponse;
import com.gadang.favorite.FavoritePlaceResponse;
import com.gadang.favorite.FavoriteService;
import com.gadang.user.UserService;
import com.gadang.user.UserSummaryResponse;
import org.springframework.stereotype.Service;

@Service
public class MypageService {

    private final MypageMapper mypageMapper;
    private final UserService userService;
    private final CommunityService communityService;
    private final FavoriteService favoriteService;

    public MypageService(
            MypageMapper mypageMapper,
            UserService userService,
            CommunityService communityService,
            FavoriteService favoriteService) {
        this.mypageMapper = mypageMapper;
        this.userService = userService;
        this.communityService = communityService;
        this.favoriteService = favoriteService;
    }

    public MypageSummaryResponse summary(Long userId) {
        return new MypageSummaryResponse(
                UserSummaryResponse.from(userService.getUser(userId)),
                mypageMapper.countTrips(userId),
                mypageMapper.sumTripCost(userId),
                communityService.findUserPosts(userId, 1, 1).totalCount(),
                favoriteService.findByUser(userId, 1, 1).totalCount());
    }

    public PageResponse<PostResponse> posts(Long userId, int page, int size) {
        return communityService.findUserPosts(userId, page, size);
    }

    public PageResponse<FavoritePlaceResponse> favorites(Long userId, int page, int size) {
        return favoriteService.findByUser(userId, page, size);
    }

    public PageResponse<TripSummaryResponse> trips(Long userId, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        return new PageResponse<>(
                mypageMapper.findTripsByUser(userId, (safePage - 1) * safeSize, safeSize),
                safePage,
                safeSize,
                mypageMapper.countTrips(userId));
    }
}
