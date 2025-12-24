package com.example.ticketing;
/*
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.*;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

public class EmqxClient {

    private final Vertx vertx;
    private final MqttClient client;
    private final Promise<Void> ready = Promise.promise();

    public EmqxClient(Vertx vertx) {
        this.vertx = vertx;

        MqttClientOptions options = new MqttClientOptions()
                .setClientId("backend-emqx")
                .setAutoKeepAlive(true)
                .setCleanSession(true);

        this.client = MqttClient.create(vertx, options);

        // ⛳ CONNECT NGAY KHI KHỞI TẠO
        client.connect(1883, "localhost", ar -> {
            if (ar.succeeded()) {
                System.out.println("✅ EmqxClient connected to fake broker");
                ready.complete();
            } else {
                System.err.println("❌ EmqxClient connect failed");
                ready.fail(ar.cause());
            }
        });
    }


    public Future<Void> publish(
            String topic,
            JsonObject payload,
            int qos,
            boolean retain
    ) {
        return ready.future()
                .compose(v ->
                        client.publish(
                                topic,
                                Buffer.buffer(payload.encode()),
                                MqttQoS.valueOf(qos),
                                false,
                                retain
                        )
                )
                .mapEmpty();
    }


    public void close() {
        if (client.isConnected()) {
            client.disconnect();
        }
    }
}
*/