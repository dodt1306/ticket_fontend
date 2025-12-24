package com.example.ticketing;

import io.vertx.core.Vertx;

public class Main {
    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();

        vertx.deployVerticle(new MainVerticle(), ar -> {
            if (ar.failed()) {
                ar.cause().printStackTrace();
                vertx.close();
            } else {
                System.out.println("✅ MainVerticle deployed");
            }
        });
    }
}
