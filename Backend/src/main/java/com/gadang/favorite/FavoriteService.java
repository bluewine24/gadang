package com.gadang.favorite;

import com.gadang.common.exception.GadangException;
import com.gadang.common.response.PageResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FavoriteService {

    private final FavoriteMapper favoriteMapper;

    public FavoriteService(FavoriteMapper favoriteMapper) {
        this.favoriteMapper = favoriteMapper;
    }

    public PageResponse<FavoritePlaceResponse> findByUser(Long userId, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        return new PageResponse<>(
                favoriteMapper.findByUser(userId, (safePage - 1) * safeSize, safeSize),
                safePage,
                safeSize,
                favoriteMapper.countByUser(userId));
    }

    @Transactional
    public void add(Long userId, Long placeId) {
        // A's future PlaceService should create/sync PLACE rows before this boundary.
        if (favoriteMapper.countPlace(placeId) == 0) {
            throw GadangException.notFound("장소를 찾을 수 없습니다.");
        }
        if (favoriteMapper.countFavorite(userId, placeId) > 0) {
            throw GadangException.badRequest("이미 즐겨찾기에 추가된 장소입니다.");
        }
        favoriteMapper.insert(userId, placeId);
    }

    @Transactional
    public void remove(Long userId, Long placeId) {
        favoriteMapper.delete(userId, placeId);
    }
}
