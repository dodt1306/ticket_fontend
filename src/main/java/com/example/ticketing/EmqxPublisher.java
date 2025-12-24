package com.example.ticketing;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.core.Vertx;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class EmqxPublisher {

    private static final String PUBLISH_API = "/api/v5/publish";

    private final WebClient client;
    private final String publishUrl;
    private final String authHeader;

    public EmqxPublisher(
            Vertx vertx,
            String baseUrl,
            String apiKey,
            String apiSecret
    ) {
        this.client = WebClient.create(vertx);
        this.publishUrl = baseUrl + PUBLISH_API;

        this.authHeader = "Basic " + Base64.getEncoder()
                .encodeToString((apiKey + ":" + apiSecret).getBytes(StandardCharsets.UTF_8));
    }

    public Future<Void> publish(
            String topic,
            JsonObject payload,
            int qos,
            boolean retain
    ) {
        Promise<Void> promise = Promise.promise();

        JsonObject body = new JsonObject()
                .put("topic", topic)
                .put("payload", payload.encode())
                .put("qos", qos)
                .put("retain", retain);

        client.postAbs(publishUrl)
                .timeout(3000)
                .putHeader("Content-Type", "application/json")
                .putHeader("Authorization", authHeader)
                .sendJsonObject(body, ar -> {

                    if (ar.failed()) {
                        promise.fail(ar.cause());
                        return;
                    }

                    HttpResponse<Buffer> resp = ar.result();
                    if (resp.statusCode() != 200) {
                        promise.fail("EMQX HTTP " + resp.statusCode());
                        return;
                    }

                    JsonObject res = resp.bodyAsJsonObject();
                    if (res == null || !res.containsKey("id")) {
                        promise.fail("EMQX publish failed: " + res);
                        return;
                    }

                    promise.complete();
                });

        return promise.future();
    }
}
