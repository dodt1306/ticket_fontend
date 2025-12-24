package com.example.ticketing.kafka;

import io.vertx.core.json.JsonObject;
import org.apache.kafka.clients.CommonClientConfigs;

import java.util.HashMap;
import java.util.Map;

public class KafkaConsumerConfig {

    public static Map<String, String> from(JsonObject appCfg) {

        JsonObject kafka = appCfg.getJsonObject("kafka");
        if (kafka == null) {
            throw new IllegalStateException("Missing 'kafka' config");
        }

        JsonObject consumer = kafka.getJsonObject("consumer");
        if (consumer == null) {
            throw new IllegalStateException("Missing 'kafka.consumer' config");
        }

        Map<String, String> cfg = new HashMap<>();

        cfg.put("bootstrap.servers",
                kafka.getString("bootstrap", kafka.getString("bootstrapServers")));

        cfg.put("key.deserializer",
                "org.apache.kafka.common.serialization.StringDeserializer");

        cfg.put("value.deserializer",
                "org.apache.kafka.common.serialization.StringDeserializer");

        cfg.put("group.id",
                consumer.getString("groupId"));

        cfg.put("auto.offset.reset",
                consumer.getString("autoOffsetReset", "latest"));

        cfg.put("enable.auto.commit",
                String.valueOf(consumer.getBoolean("enableAutoCommit", true)));

        cfg.put("max.poll.records",
                String.valueOf(consumer.getInteger("maxPollRecords", 500)));

        cfg.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT");
        cfg.put("sasl.mechanism", "PLAIN");
        cfg.put("sasl.jaas.config", "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"kafka_backend\" password=\"B@ckend#2023\";");

        return cfg;
    }
}
