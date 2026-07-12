package com.gadang.favorite;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.gadang.common.exception.GadangException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {

    @Mock
    private FavoriteMapper favoriteMapper;

    @InjectMocks
    private FavoriteService favoriteService;

    @Test
    void addRejectsMissingPlace() {
        when(favoriteMapper.countPlace(1L)).thenReturn(0);

        assertThatThrownBy(() -> favoriteService.add(1L, 1L))
                .isInstanceOf(GadangException.class)
                .hasMessage("장소를 찾을 수 없습니다.");
    }

    @Test
    void addRejectsDuplicateFavorite() {
        when(favoriteMapper.countPlace(1L)).thenReturn(1);
        when(favoriteMapper.countFavorite(1L, 1L)).thenReturn(1);

        assertThatThrownBy(() -> favoriteService.add(1L, 1L))
                .isInstanceOf(GadangException.class)
                .hasMessage("이미 즐겨찾기에 추가된 장소입니다.");
    }
}
