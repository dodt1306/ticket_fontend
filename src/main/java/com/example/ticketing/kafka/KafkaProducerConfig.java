package com.example.ticketing.kafka;

import io.vertx.core.json.JsonObject;
import org.apache.kafka.clients.CommonClientConfigs;

import java.util.HashMap;
import java.util.Map;

public class KafkaProducerConfig {

    public static Map<String, String> from(JsonObject appCfg) {

        JsonObject kafka = appCfg.getJsonObject("kafka");
        if (kafka == null) {
            throw new IllegalStateException("Missing 'kafka' config");
        }

        JsonObject producer = kafka.getJsonObject("producer");
        if (producer == null) {
            throw new IllegalStateException("Missing 'kafka.producer' config");
        }

        String bootstrap =
                kafka.getString("bootstrap", kafka.getString("bootstrapServers"));

        if (bootstrap == null) {
            throw new IllegalStateException("Missing kafka bootstrap servers");
        }

        Map<String, String> cfg = new HashMap<>();

        cfg.put("bootstrap.servers", bootstrap);
        cfg.put("key.serializer",
                "org.apache.kafka.common.serialization.StringSerializer");
        cfg.put("value.serializer",
                "org.apache.kafka.common.serialization.StringSerializer");

        // Optional tuning (string values!)
        cfg.put("acks", producer.getString("acks", "all"));
        cfg.put("retries", String.valueOf(producer.getInteger("retries", 3)));
        cfg.put("linger.ms", String.valueOf(producer.getInteger("lingerMs", 5)));
        cfg.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT");
        cfg.put("sasl.mechanism", "PLAIN");
        cfg.put("sasl.jaas.config", "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"kafka_backend\" password=\"B@ckend#2023\";");

        return cfg;
    }
}

