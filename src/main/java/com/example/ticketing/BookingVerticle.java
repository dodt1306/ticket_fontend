package com.example.ticketing;

import com.example.ticketing.api.ApiError;
import com.example.ticketing.api.ErrorCode;
import com.example.ticketing.api.Helper;
import com.example.ticketing.db.Db;
import com.example.ticketing.model.Events;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.redis.client.Command;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.Request;

public class BookingVerticle extends AbstractVerticle {

    private final Db db;
    private Redis redis;

    public BookingVerticle(Db db, Redis redis) {
        this.db = db;
        this.redis = redis;
    }

    /* ================================
       GET /events/:eventId/sections
       ================================ */
    public void getSections(RoutingContext ctx) {
        String eventId = ctx.pathParam("eventId");

        db.fetchSections(eventId)
                .onSuccess(sections ->
                        ctx.json(ok(new JsonObject()
                                .put("eventId", eventId)
                                .put("sections", sections)
                        ))
                )
                .onFailure(ctx::fail); // system error
    }

    /* =====================================================
      GET /events/:eventId/listings
      ===================================================== */
    public void getListings(RoutingContext ctx) {

        String eventId = ctx.pathParam("eventId");

        Integer quantity;
        try {
            quantity = Integer.parseInt(ctx.queryParam("quantity").get(0));
        } catch (Exception e) {
            ApiError.business(ctx, ErrorCode.INVALID_REQUEST);
            return;
        }

        boolean adjacent = !"false".equalsIgnoreCase(
                ctx.queryParam("adjacent").isEmpty()
                        ? "true"
                        : ctx.queryParam("adjacent").get(0)
        );

        db.fetchListingsBySection(eventId, quantity, adjacent)
                .onSuccess(sections -> {
                    ctx.json(ok(new JsonObject()
                            .put("eventId", eventId)
                            .put("quantity", quantity)
                            .put("adjacent", adjacent)
                            .put("sections", sections)
                    ));
                })
                .onFailure(ctx::fail);
    }

    /* =====================================================
       POST /events/:eventId/hold
       ===================================================== */
    public void holdSeats(RoutingContext ctx) {

        String visitorToken = ctx.user()
                .principal()
                .getString("visitorToken");

        String eventId = ctx.pathParam("eventId");
        JsonObject body = ctx.getBodyAsJson();

        String sectionId = body.getString("sectionId");
        Integer quantity = body.getInteger("quantity");
        Integer price = body.getInteger("price");
        Boolean adjacent = body.getBoolean("adjacent", true);

        if (sectionId == null || quantity == null || quantity <= 0
                || price == null || price <= 0) {

            ApiError.business(ctx, ErrorCode.INVALID_REQUEST);
            return;
        }

        db.holdSeats(
                        eventId,
                        visitorToken,
                        sectionId,
                        quantity,
                        price,
                        adjacent,
                        420
                )
                .onSuccess(result -> {
                    ctx.json(ok(result));
                })
                .onFailure(err -> {
                    switch (err.getMessage()) {
                        case "NO_ADJACENT_SEATS" -> ApiError.business(ctx, ErrorCode.NO_ADJACENT_SEATS);

                        case "INSUFFICIENT_SEATS",
                             "SOLD_OUT_PRICE_TIER" -> ApiError.business(ctx, ErrorCode.SEAT_ALREADY_HELD);

                        default -> ctx.fail(err);
                    }
                });
    }


    /* ================================
       GET /visitors/:visitorToken/holds
       ================================ */
    public void getVisitorHolds(RoutingContext ctx) {
        String visitorToken = ctx.pathParam("visitorToken");
        String eventId = ctx.queryParam("eventId").get(0);

        db.getActiveHoldsByVisitor(eventId, visitorToken)
                .onSuccess(holds ->
                        ctx.json(ok(new JsonObject()
                                .put("visitorToken", visitorToken)
                                .put("eventId", eventId)
                                .put("holds", holds)
                        ))
                )
                .onFailure(ctx::fail);
    }

    public void releaseHold(RoutingContext ctx) {

        String visitorToken = ctx.user()
                .principal()
                .getString("visitorToken");

        String holdToken = ctx.getBodyAsJson().getString("holdToken");

        if (holdToken == null) {
            ApiError.business(ctx, ErrorCode.INVALID_REQUEST);
            return;
        }

        db.releaseHold(holdToken, visitorToken)
                .onSuccess(v -> {
                    ctx.json(new JsonObject()
                            .put("success", true)
                            .put("code", "OK")
                    );
                })
                .onFailure(err -> {
                    if ("HOLD_NOT_FOUND_OR_FORBIDDEN".equals(err.getMessage())) {
                        ApiError.business(ctx, ErrorCode.FORBIDDEN);
                    } else {
                        ctx.fail(err);
                    }
                });
    }

    public void checkout(RoutingContext ctx) {

        String visitorToken = ctx.user()
                .principal()
                .getString("visitorToken");

        String holdToken = ctx.getBodyAsJson().getString("holdToken");

        if (holdToken == null) {
            ApiError.business(ctx, ErrorCode.INVALID_REQUEST);
            return;
        }

        db.checkoutHold(holdToken, visitorToken)
                .onSuccess(result -> {
                    ctx.json(new JsonObject()
                            .put("success", true)
                            .put("code", "OK")
                            .put("data", result)
                    );
                })
                .onFailure(err -> {
                    if ("CHECKOUT_NOT_ALLOWED".equals(err.getMessage())) {
                        ApiError.business(ctx, ErrorCode.FORBIDDEN);
                    } else {
                        ctx.fail(err);
                    }
                });
    }

    public void paymentConfirm(RoutingContext ctx) {

        JsonObject body = ctx.getBodyAsJson();
        String bookingId = body.getString("bookingId");
        String visitorToken = ctx.user()
                .principal()
                .getString("visitorToken");
        if (bookingId == null) {
            ApiError.business(ctx, ErrorCode.INVALID_REQUEST);
            return;
        }

        db.confirmPayment(bookingId)
                .onSuccess(res -> {
                    String eventId = res.getString("event_id");
                    Helper.releaseBooking(redis, eventId, visitorToken);
                    ctx.json(new JsonObject()
                            .put("success", true)
                            .put("data", res)
                    );
                })
                .onFailure(err -> {
                    if ("PAYMENT_ALREADY_PROCESSED".equals(err.getMessage())) {
                        ApiError.business(ctx, ErrorCode.ALREADY_DONE);
                    } else {
                        ctx.fail(err);
                    }
                });
    }

    public void getTickets(RoutingContext ctx) {

        String visitorToken = ctx.user()
                .principal()
                .getString("visitorToken");

        String bookingId = ctx.pathParam("id");

        if (bookingId == null) {
            ApiError.business(ctx, ErrorCode.INVALID_REQUEST);
            return;
        }

        db.getTicketsByBooking(bookingId, visitorToken)
                .onSuccess(tickets -> {
                    ctx.json(new JsonObject()
                            .put("success", true)
                            .put("data", new JsonObject()
                                    .put("bookingId", bookingId)
                                    .put("tickets", tickets)
                            )
                    );
                })
                .onFailure(err -> {
                    if ("TICKETS_NOT_AVAILABLE".equals(err.getMessage())) {
                        ApiError.business(ctx, ErrorCode.NOT_READY);
                    } else {
                        ctx.fail(err);
                    }
                });
    }

    public void getEvents(RoutingContext ctx) {

        db.getEvents()
                .onSuccess(events -> {

                    JsonArray arr = new JsonArray();
                    for (Events e : events) {
                        arr.add(JsonObject.mapFrom(e)); // POJO → JSON
                    }
                    ctx.response()
                            .putHeader("Content-Type", "application/json")
                            .end(arr.encode());
                })
                .onFailure(err -> ApiError.system(ctx, err));
    }
    public void waitingReady(RoutingContext ctx) {
        JsonObject body = ctx.getBodyAsJson();

        String visitorToken = body.getString("visitorToken");
        String eventId      = body.getString("eventId");

        if (visitorToken == null || eventId == null) {
            ctx.json(new JsonObject()
                    .put("success", false)
                    .put("code", "INVALID_REQUEST"));
            return;
        }

        String sessionKey = "session:" + visitorToken;
        String queueKey   = "event:" + eventId + ":queue";
        String readyKey   = "event:" + eventId + ":ready";

        // 1️⃣ Get session status
        redis.send(Request.cmd(Command.HGET)
                .arg(sessionKey)
                .arg("status")
        ).compose(statusReply -> {

            // ❌ Session không tồn tại
            if (statusReply == null) {
                return Future.failedFuture("SESSION_EXPIRED");
            }

            String status = statusReply.toString();

            // ✅ Idempotent cases
            if ("READY".equals(status) || "SERVED".equals(status)) {
                return Future.succeededFuture("IGNORED");
            }

            // 2️⃣ Get queue score (FIFO)
            return redis.send(Request.cmd(Command.ZSCORE)
                    .arg(queueKey)
                    .arg(visitorToken)
            ).compose(scoreReply -> {

                // ❌ Không còn trong queue
                if (scoreReply == null) {
                    return Future.failedFuture("NOT_IN_QUEUE");
                }

                // 3️⃣ Insert READY ZSET
                return redis.send(Request.cmd(Command.ZADD)
                        .arg(readyKey)
                        .arg(scoreReply.toString())
                        .arg(visitorToken)
                ).compose(v ->
                        // 4️⃣ Update session status
                        redis.send(Request.cmd(Command.HSET)
                                .arg(sessionKey)
                                .arg("status").arg("READY")
                        ).map("READY")
                );
            });
        }).onSuccess(result -> {

            if ("IGNORED".equals(result)) {
                ctx.json(new JsonObject()
                        .put("success", true)
                        .put("status", "IGNORED"));
            } else {
                ctx.json(new JsonObject()
                        .put("success", true)
                        .put("status", "READY"));
            }

        }).onFailure(err -> {

            String code = err.getMessage();

            // ❌ Business errors → 200 + success=false
            if ("SESSION_EXPIRED".equals(code)
                    || "NOT_IN_QUEUE".equals(code)) {

                ctx.json(new JsonObject()
                        .put("success", false)
                        .put("code", code));
                return;
            }

            // ❌ System error
            ApiError.system(ctx, err);
        });
    }


    /* ================================
       RESPONSE OK HELPER
       ================================ */
    private JsonObject ok(JsonObject data) {
        return new JsonObject()
                .put("success", true)
                .put("code", "OK")
                .put("data", data);
    }
}
