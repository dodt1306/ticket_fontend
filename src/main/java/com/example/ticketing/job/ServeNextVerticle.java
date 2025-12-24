package com.example.ticketing.job;

import com.example.ticketing.EmqxPublisher;
import com.example.ticketing.api.Helper;
import com.example.ticketing.db.Db;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import io.vertx.redis.client.Command;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.Request;

public class ServeNextVerticle extends AbstractVerticle {

    private final Redis redis;
    private final Db db;
    private final EmqxPublisher emqx;
    private final JWTAuth jwtAuth;
    private final KafkaProducer<String, String> producer;

    private final String serveLua = Helper.loadLua("redis/serve_next.lua");

    private static final long POLL_MS = 300;
    private static final int TOKEN_TTL_SECONDS = 600;
    private static final int REDIS_TTL_SECONDS = 600;
    private static final int MAX_BOOKING = 20;

    public ServeNextVerticle(
            Db db,
            Redis redis,
            EmqxPublisher emqx,
            JWTAuth jwtAuth,
            KafkaProducer<String, String> producer
    ) {
        this.db = db;
        this.redis = redis;
        this.emqx = emqx;
        this.jwtAuth = jwtAuth;
        this.producer = producer;
    }

    @Override
    public void start(Promise<Void> startPromise) {
        vertx.setPeriodic(POLL_MS, t -> serveLoop());
        startPromise.complete();
    }

    /* =====================================================
     * MAIN LOOP
     * ===================================================== */
    private void serveLoop() {
        redis.send(Request.cmd(Command.SMEMBERS).arg("active:queues"))
                .onSuccess(reply -> {
                    if (reply == null || reply.size() == 0) return;

                    for (int i = 0; i < reply.size(); i++) {
                        String eventId = reply.get(i).toString();
                        gateAndServe(eventId);
                    }
                })
                .onFailure(err ->
                        System.err.println("[ServeLoop] Redis error: " + err.getMessage())
                );
    }

    /* =====================================================
     * GATE: CHECK EVENT STATUS FIRST
     * ===================================================== */
    private void gateAndServe(String eventId) {
        db.loadEvent(eventId)
                .onSuccess(event -> {
                    // 🔒 INVARIANT: CHỈ SELLING MỚI ĐƯỢC POP QUEUE
                    if (!event.isSelling()) {
                        return;
                    }
                    serveNextInternal(eventId);
                })
                .onFailure(err ->
                        System.err.println("[Gate] loadEvent failed: " + err.getMessage())
                );
    }

    /* =====================================================
     * POP QUEUE + GRANT (ONLY WHEN SELLING)
     * ===================================================== */
    private void serveNextInternal(String eventId) {

        String queueKey         = "event:" + eventId + ":queue";
        String readyKey         = "event:" + eventId + ":ready";
        String bookingActiveKey = "event:" + eventId + ":booking_active";
        String activeQueueSet   = "active:queues";
        String sessionPrefix    = "session:";

        long nowMillis = System.currentTimeMillis();

        redis.send(
                        Request.cmd(Command.EVAL)
                                .arg(serveLua)
                                .arg("5")
                                .arg(readyKey)
                                .arg(queueKey)
                                .arg(bookingActiveKey)
                                .arg(activeQueueSet)
                                .arg(sessionPrefix)
                                .arg(String.valueOf(nowMillis))
                                .arg(String.valueOf(REDIS_TTL_SECONDS))
                                .arg(String.valueOf(MAX_BOOKING))
                                .arg(eventId)
                )
                .onFailure(err ->
                        System.err.println("[ServeNext] Redis error: " + err.getMessage())
                )
                .onSuccess(reply -> {

                    if (reply == null || reply.size() == 0) return;

                    String result = reply.get(0).toString();

                    if (!"OK".equals(result)) {
                        // NO_READY | EMPTY | LIMIT_REACHED | SKIP_*
                        return;
                    }

                    if (reply.size() < 2) {
                        System.err.println("[ServeNext] OK but missing visitorToken");
                        return;
                    }

                    String visitorToken = reply.get(1).toString();
                    String sessionKey = sessionPrefix + visitorToken;

                    issueAccess(eventId, visitorToken, sessionKey, nowMillis);
                });
    }

    /* =====================================================
     * ISSUE ACCESS TOKEN
     * ===================================================== */
    private void issueAccess(
            String eventId,
            String visitorToken,
            String sessionKey,
            long nowMillis
    ) {

        JsonObject claims = new JsonObject()
                .put("visitorToken", visitorToken)
                .put("eventId", eventId);

        String accessToken = jwtAuth.generateToken(
                claims,
                new JWTOptions().setExpiresInSeconds(TOKEN_TTL_SECONDS)
        );

        // Save session
        redis.send(
                Request.cmd(Command.HSET)
                        .arg(sessionKey)
                        .arg("access_token").arg(accessToken)
                        .arg("status").arg("ACTIVE")
        );

        // Kafka event
        JsonObject servedEvt = new JsonObject()
                .put("eventId", eventId)
                .put("visitorToken", visitorToken)
                .put("servedAt", nowMillis)
                .put("access_token", accessToken);
        producer.send(
                KafkaProducerRecord.create(
                        "queue.served",
                        visitorToken,
                        servedEvt.encode()
                )
        );

        // EMQX push
        JsonObject payload = new JsonObject()
                .put("type", "ACCESS_GRANTED")
                .put("eventId", eventId)
                .put("visitorToken", visitorToken)
                .put("accessToken", accessToken)
                .put("servedAt", nowMillis);

        emqx.publish(
                "visitor/" + visitorToken,
                payload,
                1,
                false
        ).onFailure(err ->
                System.err.println("[EMQX] publish failed: " + err.getMessage())
        );
    }
}
