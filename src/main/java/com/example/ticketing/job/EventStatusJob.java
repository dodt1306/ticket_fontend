package com.example.ticketing.job;

import com.example.ticketing.EmqxPublisher;
import com.example.ticketing.db.Db;
import com.example.ticketing.repository.EventRepository;
import com.example.ticketing.repository.PgEventRepository;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Pool;

public class EventStatusJob  extends AbstractVerticle {

    private final EventRepository repo;
    private final EmqxPublisher emqx;
    public EventStatusJob(EventRepository repo, EmqxPublisher emqx) {
        this.repo = repo;
        this.emqx = emqx;
    }
    @Override
    public void start() {
        // chạy mỗi 1s
        Vertx.currentContext().owner()
                .setPeriodic(1000, id -> tick());
    }

    private void tick() {
        lockedToWaiting();
        waitingToSelling();
        endedToSelling();      // ✅ ADD
        sellingToEnded();
    }

    /* ========== LOCKED → WAITING ========== */
    private void lockedToWaiting() {
        repo.updateLockedToWaiting()
                .onSuccess(ids -> ids.forEach(id ->
                        publish(id, "WAITING")
                ));
    }

    /* ========== WAITING → SELLING ========== */
    private void waitingToSelling() {
        repo.updateWaitingToSelling()
                .onSuccess(ids -> ids.forEach(id ->
                        publish(id, "SELLING")
                ));
    }

    /* ========== SELLING → ENDED ========== */
    private void sellingToEnded() {
        repo.updateSellingToEnded()
                .onSuccess(ids -> ids.forEach(id ->
                        publish(id, "ENDED")
                ));
    }
    /* ========== ENDED → SELLING (RESUME) ========== */
    private void endedToSelling() {
        repo.updateEndedToSelling()
                .onSuccess(ids -> ids.forEach(id ->
                        publish(id, "SELLING")
                ));
    }
    /* ========== EMQX PUBLISH ========== */
    private void publish(String eventId, String status) {
        JsonObject msg = new JsonObject()
                .put("type", "EVENT_STATUS")
                .put("eventId", eventId)
                .put("status", status);
        emqx.publish("event/" + eventId, msg, 1, false)
                .onFailure(err ->
                        System.err.println("EMQX publish failed: " + err.getMessage())
                );
        System.out.println(
                "[JOB][EMQX] " + eventId + " → " + status
        );
    }


}
