package com.example.ticketing.job;


import com.example.ticketing.api.Helper;
import com.example.ticketing.db.Db;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.Request;
import io.vertx.redis.client.Command;

import java.time.Instant;
import java.util.List;

/**
 * BookingCleanupJob
 *
 * - Dọn booking_active hết hạn (ZSET expireAt)
 * - KHÔNG phụ thuộc serveNext
 * - An toàn khi event INACTIVE
 */
public class BookingCleanupJob extends AbstractVerticle {

    private final Redis redis;
    private final Db db;

    // ===== CONFIG =====
    private static final long CLEANUP_INTERVAL_MS = 3_000;
    private static final long RECENT_EVENT_WINDOW_MS = 10_000;
    public BookingCleanupJob(Redis redis, Db db) {
        this.redis = redis;
        this.db = db;
    }

    // =========================
    // START JOB
    // =========================
    @Override
    public void start() {
        vertx.setPeriodic(CLEANUP_INTERVAL_MS, id -> runOnce());
        System.out.println("[BookingCleanupJob] started");
    }

    // =========================
    // RUN CLEANUP
    // =========================
    private void runOnce() {
        long nowMillis = System.currentTimeMillis();

        loadEventsForCleanup(nowMillis)
                .onSuccess(eventIds -> {
                    for (String eventId : eventIds) {
                        cleanupBookingActive(eventId, nowMillis);
                        cleanupQueueGhost(eventId);
                    }
                })
                .onFailure(err ->
                        System.err.println("[BookingCleanupJob] load events failed: " + err.getMessage())
                );


    }

    // =========================
    // LOAD EVENTS NEED CLEANUP
    // =========================
    private Future<List<String>> loadEventsForCleanup(long nowMillis) {
        // Lấy:
        // - event đang mở
        // - event vừa đóng
        return db.loadEventsForCleanup(
                Instant.ofEpochMilli(nowMillis - RECENT_EVENT_WINDOW_MS)
        );
    }

    // =========================
    // CLEANUP 1 EVENT
    // =========================
    private void cleanupBookingActive(String eventId, long nowMillis) {

        String bookingActiveKey = "event:" + eventId + ":booking_active";

        redis.send(
                        Request.cmd(Command.ZREMRANGEBYSCORE)
                                .arg(bookingActiveKey)
                                .arg("-inf")
                                .arg(String.valueOf(nowMillis))
                )
                .onFailure(err ->
                        System.err.println("[BookingCleanupJob] cleanup failed for "
                                + eventId + ": " + err.getMessage())
                );
    }

    // =========================
    // OPTIONAL: HARD CLEAN EVENT
    // =========================
    public void cleanupClosedEventHard(String eventId) {

        redis.send(Request.cmd(Command.DEL)
                .arg("event:" + eventId + ":booking_active"));

        redis.send(Request.cmd(Command.DEL)
                .arg("event:" + eventId + ":queue"));

        redis.send(Request.cmd(Command.SREM)
                .arg("active:queues")
                .arg(eventId));

        System.out.println("[BookingCleanupJob] hard cleaned event " + eventId);
    }


    private final String CLEANUP_QUEUE_LUA = Helper.loadLua("redis/cleanup_queue.lua");
    private static final int QUEUE_CLEANUP_BATCH = 200;
    private void cleanupQueueGhost(String eventId) {

        String queueKey = "event:" + eventId + ":queue";
        String readyKey = "event:" + eventId + ":ready";

        redis.send(
                Request.cmd(Command.EVAL)
                        .arg(CLEANUP_QUEUE_LUA)
                        .arg("3")
                        .arg(queueKey)
                        .arg(readyKey)
                        .arg("session:")
                        .arg(String.valueOf(QUEUE_CLEANUP_BATCH))
        ).onSuccess(reply -> {
            long removed = reply == null ? 0 : reply.toLong();
            if (removed > 0) {
                System.out.println("[BookingCleanupJob] cleaned "
                        + removed + " ghost queue entries for " + eventId);
            }
        }).onFailure(err ->
                System.err.println("[BookingCleanupJob] queue cleanup failed for "
                        + eventId + ": " + err.getMessage())
        );
    }
}
