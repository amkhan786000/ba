package com.rahbar.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/** A page of results in the shape the website expects: {content: [...], page: {size, number, totalElements, totalPages}}. */
public record PageBody<T>(List<T> content, PageInfo page) {

    public record PageInfo(int size, int number, long totalElements, int totalPages) {
    }

    public static <T> PageBody<T> of(Page<T> p) {
        return new PageBody<>(p.getContent(), new PageInfo(p.getSize(), p.getNumber(), p.getTotalElements(), p.getTotalPages()));
    }
}
