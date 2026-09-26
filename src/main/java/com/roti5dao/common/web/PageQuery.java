package com.roti5dao.common.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** paging ที่จำกัดขนาดสูงสุด ป้องกันการดึงข้อมูลจำนวนมากในครั้งเดียว */
public final class PageQuery {

    public static final int MAX_SIZE = 100;

    private PageQuery() {
    }

    public static Pageable of(Integer page, Integer size, Sort sort) {
        int p = page == null || page < 0 ? 0 : page;
        int s = size == null || size < 1 ? 20 : Math.min(size, MAX_SIZE);
        return PageRequest.of(p, s, sort);
    }
}
