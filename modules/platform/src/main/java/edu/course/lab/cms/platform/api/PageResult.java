package edu.course.lab.cms.platform.api;

import java.util.List;

public record PageResult<T>(List<T> items, long page, long size, long total) {
    public PageResult {
        items = List.copyOf(items);
    }
}
