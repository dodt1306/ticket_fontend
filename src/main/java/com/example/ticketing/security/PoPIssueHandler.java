package com.example.ticketing.security;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.redis.client.Command;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.Request;

import java.util.UUID;

public class PoPIssueHandler {

    private final Redis redis;

    public PoPIssueHandler(Redis redis) {
        this.redis = redis;
    }

    public void issue(RoutingContext ctx) {

        String visitorToken = ctx.user()
                .principal()
                .getString("visitorToken");

        String clientId = visitorToken;
        String popToken = UUID.randomUUID().toString();
        long expiresAt = System.currentTimeMillis() + 10_000;

        redis.send(
                Request.cmd(Command.HSET)
                        .arg("pop:" + popToken)
                        .arg("visitorToken").arg(visitorToken)
                        .arg("clientId").arg(clientId)
                        .arg("expiresAt").arg(String.valueOf(expiresAt))
        ).onSuccess(v ->
                redis.send(
                        Request.cmd(Command.EXPIRE)
                                .arg("pop:" + popToken)
                                .arg("10")
                ).onSuccess(x -> {
                    ctx.response().end(
                            new JsonObject()
                                    .put("popToken", popToken)
                                    .encode()
                    );
                })
        );
    }
}
