package com.petshop.dto.admin;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class StockReportRow {
    private String sku;
    private String productName;
    private int currentStock;
    private long imported;
    private long reservedOrSold;
    private long expected;
    private long difference;
    private BigDecimal estimatedValue;
}
