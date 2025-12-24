package com.example.ticketing;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.impl.Connection;

import javax.sql.DataSource;
import java.sql.DriverManager;
import java.util.List;

public class PgPoolFactory {
    public static PgPool create(Vertx vertx, JsonObject db) {
        PgConnectOptions connectOptions = new PgConnectOptions()
                .setHost(db.getString("host"))
                .setPort(db.getInteger("port", 5432))
                .setDatabase(db.getString("database"))
                .setUser(db.getString("user"))
                .setPassword(db.getString("password"));

        // ⭐ SET SCHEMA (search_path)
        String schema = db.getString("schema");
        if (schema != null && !schema.isBlank()) {
            connectOptions.addProperty("options", "-c search_path="+schema);
        }

        PoolOptions poolOptions = new PoolOptions()
                .setMaxSize(db.getInteger("poolSize", 20));

        return PgPool.pool(vertx, connectOptions, poolOptions);
    }
}