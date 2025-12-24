package com.example.ticketing.model;

public record ListingRef(
        String eventId,
        String sectionId,
        String rowId,
        int startSeat,
        int quantity
) {}