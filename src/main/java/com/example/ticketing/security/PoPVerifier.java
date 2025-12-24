package com.example.ticketing.security;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.redis.client.Command;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.Request;
import io.vertx.redis.client.Response;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class PoPVerifier {

    private final Redis redis;
    private final WebClient webClient;
    private final String emqxApiBase;
    private final String apiKey;
    private final String apiSecret;
    private final String authHeader;
    public PoPVerifier(
            Vertx vertx,
            Redis redis,
            String emqxApiBase,
            String apiKey,
            String apiSecret
    ) {
        this.redis = redis;
        this.webClient = WebClient.create(vertx);
        this.emqxApiBase = emqxApiBase;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.authHeader = "Basic " + Base64.getEncoder()
                .encodeToString((apiKey + ":" + apiSecret).getBytes(StandardCharsets.UTF_8));
    }

    public Future<Void> verify(String popToken, String visitorToken) {

        Promise<Void> promise = Promise.promise();
        String key = "pop:" + popToken;

        redis.send(Request.cmd(Command.HGETALL).arg(key), ar -> {

            if (ar.failed() || ar.result().size() == 0) {
                promise.fail("POP_NOT_FOUND");
                return;
            }
            Response res = ar.result();
            Map<String, String> pop = new HashMap<>();
            for (String field : res.getKeys()) {
                pop.put(field, res.get(field).toString());
            }

            if (!visitorToken.equals(pop.get("visitorToken"))) {
                promise.fail("POP_MISMATCH");
                return;
            }

            long expiresAt = Long.parseLong(pop.get("expiresAt"));
            if (System.currentTimeMillis() > expiresAt) {
                redis.send(Request.cmd(Command.DEL).arg(key));
                promise.fail("POP_EXPIRED");
                return;
            }

            String clientId = pop.get("clientId");

            verifyClientConnected(clientId)
                    .onFailure(promise::fail)
                    .onSuccess(v -> {
                        // consume PoP (one-time)
                        redis.send(Request.cmd(Command.DEL).arg(key));
                        promise.complete();
                    });
        });

        return promise.future();
    }

    private Future<Void> verifyClientConnected(String clientId) {

        Promise<Void> promise = Promise.promise();

        webClient
                .getAbs(emqxApiBase + "/api/v5/clients/" + clientId)
                .putHeader("Authorization", this.authHeader)
                .send(ar -> {

                    if (ar.failed()) {
                        promise.fail("EMQX_API_FAIL");
                        return;
                    }
                    JsonObject data =
                            ar.result().bodyAsJsonObject();
                    if (data == null || !data.getBoolean("connected", false)) {
                        promise.fail("MQTT_NOT_CONNECTED");
                    } else {
                        promise.complete();
                    }
                });

        return promise.future();
    }
}
