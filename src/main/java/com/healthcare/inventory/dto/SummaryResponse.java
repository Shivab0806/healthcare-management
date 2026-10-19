package com.healthcare.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class SummaryResponse {
    private long total;
    private long expired;
    private long lowStock;
    private long expiringSoon;
}
