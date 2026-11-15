package com.petshop.service;

import com.petshop.repository.ProductImageRepository;
import java.util.*;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Batch product pictures without collection fetch joins on paginated reviews. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductThumbnailService {
    public static final String PLACEHOLDER = "/images/product-placeholder.svg";
    private final ProductImageRepository images;

    public Map<Long, String> forProducts(Collection<Long> ids) {
        return resolve(ids, images::productThumbnails);
    }

    public Map<Long, String> forOrderItems(Collection<Long> ids) {
        return resolve(ids, images::orderItemThumbnails);
    }

    private Map<Long, String> resolve(Collection<Long> source,
            Function<Collection<Long>, List<ProductImageRepository.Thumbnail>> query) {
        Set<Long> ids = new LinkedHashSet<>();
        source.stream().filter(Objects::nonNull).forEach(ids::add);
        if (ids.isEmpty()) return Map.of();
        Map<Long, String> result = new LinkedHashMap<>();
        ids.forEach(id -> result.put(id, PLACEHOLDER));
        Set<Long> selected = new HashSet<>();
        for (var image : query.apply(ids)) {
            String url = image.getImageUrl();
            if (url != null && !url.isBlank() && selected.add(image.getOwnerId())) {
                result.put(image.getOwnerId(), url.strip());
            }
        }
        return result;
    }
}
