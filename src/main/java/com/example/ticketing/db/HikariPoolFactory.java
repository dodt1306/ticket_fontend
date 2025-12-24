package com.example.ticketing.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.vertx.core.json.JsonObject;

import javax.sql.DataSource;

public class HikariPoolFactory {


    public static DataSource create(JsonObject pgCfg)  {

        JsonObject hikari = pgCfg.getJsonObject("hikari", new JsonObject());
        HikariConfig hc = new HikariConfig();
        // JDBC URL build từ postgres config
        hc.setJdbcUrl(HikariPoolFactory.buildJdbcUrl(pgCfg));
        hc.setUsername(pgCfg.getString("user"));
        hc.setPassword(pgCfg.getString("password"));

        // ===== tuning =====
        hc.setMaximumPoolSize(hikari.getInteger("maxPoolSize", 4));
        hc.setMinimumIdle(hikari.getInteger("minIdle", 1));
        hc.setConnectionTimeout(
                hikari.getLong("connectionTimeoutMs", 10_000L)
        );
        hc.setIdleTimeout(
                hikari.getLong("idleTimeoutMs", 60_000L)
        );
        hc.setMaxLifetime(
                hikari.getLong("maxLifetimeMs", 600_000L)
        );

        // Optional but recommended
        hc.setAutoCommit(true);
        hc.setPoolName("pg-copy-pool");
        DataSource dataSource =
                new HikariDataSource(hc);
        return dataSource;
    }
    public static String buildJdbcUrl(JsonObject pg) {
        String host = pg.getString("host");
        Integer port = pg.getInteger("port", 5432);
        String db = pg.getString("database");
        String schema = pg.getString("schema");

        if (host == null || db == null) {
            throw new IllegalStateException("Missing postgres host/database");
        }
        String url = "jdbc:postgresql://" + host + ":" + port + "/" + db;
        if (schema != null) {
            url += "?currentSchema=" + schema;
        }
        return url;
    }
}