package com.example.ticketing;

import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisOptions;
import io.vertx.core.Vertx;

public class RedisClientFactory {
    public static Redis create(Vertx vertx, String host, int port) {
        RedisOptions opts = new RedisOptions()
                .setConnectionString("redis://" + host + ":" + port)

                // 🔑 tăng số connection trong pool
                .setMaxPoolSize(32)

                // 🔁 số request được chờ khi pool đầy
                .setMaxPoolWaiting(1000)

                // ⏱ timeout chờ lấy connection (ms)
                .setPoolRecycleTimeout(3000);

        return Redis.createClient(vertx, opts);
    }
}
