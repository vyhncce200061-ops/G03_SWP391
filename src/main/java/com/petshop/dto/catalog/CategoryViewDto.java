package com.petshop.dto.catalog;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Detached category data: templates never traverse a lazy entity hierarchy. */
@Getter
@AllArgsConstructor
public class CategoryViewDto {
    private final Integer id;
    private final String code;
    private final String name;
    private final String description;
    private final Integer sortOrder;
    private final Boolean isActive;
    private final Integer parentId;
    private final String parentName;
    private final boolean parentActive;
    private final List<CategoryViewDto> children;

    public String getDisplayName() {
        return parentId == null ? name : parentName + " / " + name;
    }

    public boolean isVisible() {
        return Boolean.TRUE.equals(isActive) && parentActive;
    }

    public String getOptionLabel() {
        return getDisplayName() + (isVisible() ? "" : " (ngừng hiển thị)");
    }
}
