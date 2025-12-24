package com.example.ticketing;

import com.example.ticketing.db.Db;
import io.vertx.core.AbstractVerticle;

public class HoldExpiryVerticle extends AbstractVerticle {

    private Db db;

    public HoldExpiryVerticle(Db db) {
        this.db = db;
    }

    @Override
    public void start() {
        vertx.setPeriodic(5_000, id -> db.expireHolds());
    }
}