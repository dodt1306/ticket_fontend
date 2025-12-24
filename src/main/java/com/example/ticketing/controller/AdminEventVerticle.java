package com.example.ticketing.controller;

import com.example.ticketing.api.Helper;
import com.example.ticketing.db.Db;
import com.example.ticketing.repository.AdminEventRepository;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.redis.client.Command;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.Request;

/**
 * AdminEventVerticle
 *
 * Routes:
 *  - POST /admin/events/:eventId/actions/open-waiting
 *  - POST /admin/events/:eventId/actions/open-selling
 *  - POST /admin/events/:eventId/actions/close-selling
 */
public class AdminEventVerticle extends AbstractVerticle {

    private final AdminEventRepository repo;
    Redis redis;
    public AdminEventVerticle(AdminEventRepository adminEventRepository, Redis redis) {
        this.repo = adminEventRepository;
        this.redis = redis;
    }

    /* ================================
       POST /admin/events/:eventId/actions/open-waiting
       ================================ */
    public void openWaiting(RoutingContext ctx) {
        String eventId = ctx.pathParam("eventId");

        repo.openWaiting(eventId)
                .onSuccess(v ->
                        ctx.json(ok(eventId, "OPEN_WAITING"))
                )
                .onFailure(ctx::fail);
    }

    /* ================================
       POST /admin/events/:eventId/actions/open-selling
       ================================ */
    public void openSelling(RoutingContext ctx) {
        String eventId = ctx.pathParam("eventId");

        repo.openSelling(eventId)
                .onSuccess(v ->
                        ctx.json(ok(eventId, "OPEN_SELLING"))
                )
                .onFailure(ctx::fail);
    }

    /* ================================
       POST /admin/events/:eventId/actions/close-selling
       ================================ */
    public void closeSelling(RoutingContext ctx) {
        String eventId = ctx.pathParam("eventId");

        repo.closeSelling(eventId)
                .onSuccess(v ->
                        ctx.json(ok(eventId, "CLOSE_SELLING"))
                )
                .onFailure(ctx::fail);
    }

    /* ================================
   GET /admin/events
   ================================ */
    public void getEvents(RoutingContext ctx) {
        repo.getAllEvents()
                .onSuccess(events ->
                        ctx.json(ok(new JsonObject()
                                .put("events", events)
                        ))
                )
                .onFailure(ctx::fail);
    }

    public void resetEvent(RoutingContext ctx) {
        String eventId = ctx.pathParam("eventId");
        repo.resetEventToLocked(eventId)
                .compose(v -> Helper.devResetEventRedis(redis,eventId))
                .onSuccess(info -> {
                    System.out.println("Redis reset: " + info);
                    ctx.json(ok(eventId,"RESET_EVENT"));
                })
                .onFailure(ctx::fail);
    }


    /* ================================
       Response helper (same style)
       ================================ */
    private JsonObject ok(String eventId, String action) {
        return new JsonObject()
                .put("success", true)
                .put("data", new JsonObject()
                        .put("eventId", eventId)
                        .put("action", action)
                );
    }
    /* ================================
   Response helper (GENERIC)
   ================================ */
    private JsonObject ok(JsonObject data) {
        return new JsonObject()
                .put("success", true)
                .put("data", data);
    }

}
