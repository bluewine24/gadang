package com.gadang.admin;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AdmissionFee {
    private Long feeId;
    private Long placeId;
    private String placeName;
    private Integer fee;
    private String feeType;
    private LocalDateTime updatedAt;
}
