package com.example.ticketing;

import com.example.ticketing.db.Db;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;

import java.util.*;

public class KafkaQueueConsumerVerticle extends AbstractVerticle {

    private final int BATCH_SIZE = 1000;

    private KafkaConsumer<String, String> consumer;
    private KafkaProducer<String, String> producer;
    private Db db;
    private JsonObject appCfg;

    private final List<JsonObject> enqueueBuffer = Collections.synchronizedList(new ArrayList<>());
    private final List<JsonObject> servedBuffer = Collections.synchronizedList(new ArrayList<>());

    private volatile boolean flushing = false;

    public KafkaQueueConsumerVerticle(
            JsonObject appCfg,
            Db db,
            KafkaProducer<String, String> producer,
            KafkaConsumer<String, String> consumer
    ) {
        this.appCfg = appCfg;
        this.db = db;
        this.producer = producer;
        this.consumer = consumer;
    }

    @Override
    public void start(Promise<Void> startPromise) {

        JsonObject topicCfg = appCfg.getJsonObject("kafka").getJsonObject("topic");
        String enqueueTopic = topicCfg.getString("enqueue");
        String servedTopic = topicCfg.getString("served");
        Set<String> topics = new HashSet<>();
        topics.add(enqueueTopic);
        topics.add(servedTopic);
        consumer.subscribe(topics);

        consumer.handler(record -> {
            try {
                JsonObject evt = new JsonObject(record.value());

                if (record.topic().equals(enqueueTopic)) {
                    enqueueBuffer.add(evt);
                } else if (record.topic().equals(servedTopic)) {
                    servedBuffer.add(evt);
                }

                if ((enqueueBuffer.size() + servedBuffer.size()) >= BATCH_SIZE && !flushing) {
                    flushBatch();
                }

            } catch (Exception e) {
                System.err.println("Invalid event payload: " + e.getMessage());
            }
        });

        vertx.setPeriodic(1000, id -> {
            if ((!enqueueBuffer.isEmpty() || !servedBuffer.isEmpty()) && !flushing) {
                flushBatch();
            }
        });

        startPromise.complete();
    }

    private void flushBatch() {
        if (flushing) return;
        flushing = true;

        consumer.pause();

        final List<JsonObject> enqueueBatch;
        final List<JsonObject> servedBatch;

        synchronized (this) {
            enqueueBatch = new ArrayList<>(enqueueBuffer);
            servedBatch = new ArrayList<>(servedBuffer);
            enqueueBuffer.clear();
            servedBuffer.clear();
        }

        vertx.executeBlocking(promise -> {
            try {
                if (!enqueueBatch.isEmpty()) {
                    db.copyQueueBatch(enqueueBatch);
                }

                if (!servedBatch.isEmpty()) {
                    db.updateServedBatch(servedBatch);
                }

                consumer.commit();
                promise.complete();

            } catch (Exception ex) {
                System.err.println("Batch DB flush failed: " + ex.getMessage());

                // DLQ for both types
                for (JsonObject e : enqueueBatch) {
                    producer.send(
                            KafkaProducerRecord.create("queue.events.dlq",
                                    e.getString("visitorToken"), e.encode())
                    );
                }
                for (JsonObject e : servedBatch) {
                    producer.send(
                            KafkaProducerRecord.create("queue.events.dlq",
                                    e.getString("visitorToken"), e.encode())
                    );
                }

                promise.fail(ex);
            }
        }, res -> {
            flushing = false;
            consumer.resume();
        });
    }
}
