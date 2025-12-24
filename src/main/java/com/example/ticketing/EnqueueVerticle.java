package com.example.ticketing;

import com.example.ticketing.api.Helper;
import com.example.ticketing.db.Db;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import io.vertx.redis.client.Command;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

public class EnqueueVerticle extends AbstractVerticle {
    private static final Logger log =
            LoggerFactory.getLogger(EnqueueVerticle.class);
    private Redis redis;
    private final Db db;
    private final KafkaProducer<String, String> producer;
    private final String enqueueLua = Helper.loadLua("redis/enqueue.lua");

    // ✅ Inject Db + Producer
    public EnqueueVerticle(Db db, KafkaProducer<String, String> producer, Redis redis) {
        this.db = db;
        this.producer = producer;
        this.redis = redis;
    }

    public void handleEnqueue(RoutingContext ctx) {

        // ===== 1. Parse & validate input =====
        JsonObject body = ctx.getBodyAsJson();
        if (body == null) {
            ctx.response().setStatusCode(400)
                    .end(new JsonObject().put("error", "INVALID_BODY").encode());
            return;
        }
        log.info("Received enqueue request");

        String visitorToken = body.getString("visitorToken");
        String eventId = body.getString("eventId", "EVT_DEFAULT");

        if (visitorToken == null || visitorToken.isBlank()) {
            ctx.response().setStatusCode(400)
                    .end(new JsonObject().put("error", "MISSING_VISITOR_TOKEN").encode());
            return;
        }

        // ===== 2. Redis keys =====
        String counterKey = "event:" + eventId + ":counter";
        String queueKey = "event:" + eventId + ":queue";
        String sessionKey = "session:" + visitorToken;

        String now = Instant.now().toString();
        int sessionTTL = 300; // 5 phút

        // ===== 3. Execute Redis Lua (atomic) =====
        redis.send(
                        Request.cmd(Command.EVAL)
                                .arg(enqueueLua)     // nội dung file enqueue.lua
                                .arg("4")
                                .arg(counterKey)
                                .arg(queueKey)
                                .arg(sessionKey)
                                .arg("active:queues")
                                .arg(visitorToken)
                                .arg(eventId)
                                .arg(now)
                                .arg(String.valueOf(sessionTTL))
                )
                .onFailure(err -> {
                    ctx.response().setStatusCode(500)
                            .end(new JsonObject()
                                    .put("error", "REDIS_ERROR")
                                    .put("message", err.getMessage())
                                    .encode());
                })
                .onSuccess(reply -> {

                    String status = reply.get(0).toString(); // OK | EXISTS
                    long sequence = reply.get(1).toLong();


                    // ===== 4. Get live queue position =====
                    redis.send(
                                    Request.cmd(Command.ZRANK)
                                            .arg(queueKey)
                                            .arg(visitorToken)
                            )
                            .onFailure(err -> {
                                ctx.response().setStatusCode(500)
                                        .end(new JsonObject().put("error", "ZRANK_FAILED").encode());
                            })
                            .onSuccess(rankReply -> {

                                long position = (rankReply == null)
                                        ? -1
                                        : rankReply.toLong() + 1;
                                if ("OK".equals(status)) {
                                    // ===== 5. Kafka publish (best effort) =====
                                    JsonObject evt = new JsonObject()
                                            .put("type", "QUEUE_ENQUEUED")
                                            .put("visitorToken", visitorToken)
                                            .put("eventId", eventId)
                                            .put("sequence", sequence)
                                            .put("queuePosition", position)
                                            .put("queueTimestamp", now);

                                    KafkaProducerRecord<String, String> record =
                                            KafkaProducerRecord.create(
                                                    "queue.events",
                                                    visitorToken,
                                                    evt.encode()
                                            );

                                    producer.send(record, ar -> {
                                        if (ar.failed()) {
                                            System.err.println("Kafka publish failed: " + ar.cause());
                                        }
                                    });
                                }
                                // ===== 6. Response =====
                                ctx.response()
                                        .putHeader("Content-Type", "application/json")
                                        .end(new JsonObject()
                                                .put("status", status)
                                                .put("sequence", sequence)
                                                .put("queuePosition", position)
                                                .put("ttlSeconds", sessionTTL)
                                                .encode());
                            });
                });
    }


}
