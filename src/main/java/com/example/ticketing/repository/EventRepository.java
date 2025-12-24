package com.example.ticketing.repository;

import io.vertx.core.Future;
import java.util.List;

public interface EventRepository {

    /**
     * LOCKED -> WAITING
     */
    Future<List<String>> updateLockedToWaiting();

    /**
     * WAITING -> SELLING
     */
    Future<List<String>> updateWaitingToSelling();

    /**
     * SELLING -> ENDED
     */
    Future<List<String>> updateSellingToEnded();

    /* ========== ENDED → SELLING ========== */
    Future<List<String>> updateEndedToSelling();
}