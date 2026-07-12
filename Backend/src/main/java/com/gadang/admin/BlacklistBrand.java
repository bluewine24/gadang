package com.gadang.admin;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class BlacklistBrand {
    private Long id;
    private String brandName;
    private LocalDateTime registeredAt;
}
