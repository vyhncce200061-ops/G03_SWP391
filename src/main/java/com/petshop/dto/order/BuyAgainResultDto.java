package com.petshop.dto.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuyAgainResultDto {
    private int addedCount;
    private int totalRequested;
    @Builder.Default
    private List<String> unavailableItems = new ArrayList<>();
    private String message;
}
