package com.shopee.monolith.modules.product.service;

import com.shopee.monolith.modules.product.dto.internal.ProductStockSummaryDto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ProductStockSummaryProvider {

    Map<UUID, ProductStockSummaryDto> getStockSummariesByVariantIds(List<UUID> variantIds);
}
