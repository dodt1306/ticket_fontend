package com.example.ticketing.repository;

import io.vertx.core.Future;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;

import java.util.ArrayList;
import java.util.List;

public class PgEventRepository implements EventRepository {

    private final SqlClient pool;

    public PgEventRepository(SqlClient pool) {
        this.pool = pool;
    }

    @Override
    public Future<List<String>> updateLockedToWaiting() {
        return pool
                .query("""
                UPDATE events
                SET status = 'WAITING'
                WHERE status = 'LOCKED'
                  AND sale_start_time <= NOW()
                RETURNING event_id
            """)
                .execute()
                .map(this::mapEventIds);
    }

    @Override
    public Future<List<String>> updateWaitingToSelling() {
        return pool
                .query("""
                UPDATE events
                SET status = 'SELLING'
                WHERE status = 'WAITING'
                  AND sale_open_at <= NOW()
                RETURNING event_id
            """)
                .execute()
                .map(this::mapEventIds);
    }

    @Override
    public Future<List<String>> updateSellingToEnded() {
        return pool
                .query("""
                UPDATE events
                SET status = 'ENDED'
                WHERE status = 'SELLING'
                  AND sale_end_time <= NOW()
                RETURNING event_id
            """)
                .execute()
                .map(this::mapEventIds);
    }
    @Override
    public Future<List<String>> updateEndedToSelling() {
        return pool
                .preparedQuery("""
            UPDATE events
            SET status = 'SELLING'
            WHERE status = 'ENDED'
              AND sale_open_at <= now()
              AND (sale_end_time IS NULL OR sale_end_time > now())
            RETURNING event_id
        """)
                .execute()
                .map(rs -> {
                    List<String> ids = new ArrayList<>();
                    rs.forEach(row -> ids.add(row.getString("event_id")));
                    return ids;
                });
    }

    private List<String> mapEventIds(RowSet<Row> rs) {
        List<String> ids = new ArrayList<>();
        for (Row r : rs) {
            ids.add(r.getString("event_id"));
        }
        return ids;
    }
}
