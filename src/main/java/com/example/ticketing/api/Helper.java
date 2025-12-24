package com.example.ticketing.api;

import com.example.ticketing.model.ListingRef;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.redis.client.Command;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.Request;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class Helper {
    public static String loadLua(String path) {
        try (InputStream is = Helper.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new RuntimeException("Lua script not found: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load lua script", e);
        }
    }

    public static ListingRef parseListingId(String listingId) {
        String[] parts = listingId.split("\\|");
        if (parts.length != 6 || !"LST".equals(parts[0])) {
            throw new IllegalArgumentException("INVALID_LISTING_ID");
        }
        return new ListingRef(
                parts[1],
                parts[2],
                parts[3],
                Integer.parseInt(parts[4]),
                Integer.parseInt(parts[5])
        );
    }
    public static  Future<JsonObject> devResetEventRedis(Redis redis,String eventId) {
        String queueKey = "event:" + eventId + ":queue";
        String readyKey = "event:" + eventId + ":ready";
        String bookingActiveKey = "event:" + eventId + ":booking_active";
        String activeQueuesKey = "active:queues";

        return redis
                .send(Request.cmd(Command.EVAL)
                        .arg(loadLua("redis/reset_event.lua"))
                        .arg("5")
                        .arg(queueKey)
                        .arg(readyKey)
                        .arg(bookingActiveKey)
                        .arg(activeQueuesKey)
                        .arg("session:")
                        .arg(eventId)
                )
                .map(resp -> new JsonObject()
                        .put("sessionDeleted", resp.get(0).toInteger())
                        .put("queueCount",     resp.get(1).toInteger())
                        .put("readyCount",     resp.get(2).toInteger())
                );
    }


    public static void releaseBooking(
            Redis redis,
            String eventId,
            String visitorToken
    ) {
        String bookingActiveKey = "event:" + eventId + ":booking_active";

        redis.send(
                        Request.cmd(Command.ZREM)
                                .arg(bookingActiveKey)
                                .arg(visitorToken)
                )
                .onFailure(err -> {
                    System.err.println("ZREM booking_active failed: " + err.getMessage());
                })
                .onSuccess(reply -> {
                    long removed = reply.toLong(); // 🔥 SỐ PHẦN TỬ BỊ XÓA

                    if (removed == 0) {
                        System.err.println(
                                "releaseBooking: visitorToken NOT FOUND in booking_active"
                                        + " eventId=" + eventId
                                        + " visitorToken=" + visitorToken
                        );
                    } else {
                        System.out.println(
                                "releaseBooking OK eventId=" + eventId
                                        + " visitorToken=" + visitorToken
                        );
                    }
                });
    }


}
