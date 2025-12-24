package com.example.ticketing.repository;


import com.example.ticketing.api.Helper;
import com.example.ticketing.model.Events;
import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.Tuple;

import java.util.ArrayList;
import java.util.List;

public class AdminEventRepository {

    private final SqlClient pool;

    public AdminEventRepository(SqlClient pool) {
        this.pool = pool;
    }

    /* ================================
       OPEN WAITING
       LOCKED -> WAITING
       ================================ */
    public Future<Void> openWaiting(String eventId) {
        return pool
                .preparedQuery("""
                UPDATE events
                SET sale_start_time = now()
                WHERE event_id = $1
                  AND status = 'LOCKED'
            """)
                .execute(Tuple.of(eventId))
                .mapEmpty();
    }

    /* ================================
       OPEN SELLING (START / RESUME)
       WAITING / ENDED -> SELLING
       ================================ */
    public Future<Void> openSelling(String eventId) {
        return pool
                .preparedQuery("""
                UPDATE events
                SET sale_open_at = now(),
                    sale_end_time = NULL
                WHERE event_id = $1
                  AND status IN ('WAITING', 'ENDED')
            """)
                .execute(Tuple.of(eventId))
                .mapEmpty();
    }

    /* ================================
       CLOSE SELLING (PAUSE)
       SELLING -> ENDED
       ================================ */
    public Future<Void> closeSelling(String eventId) {
        return pool
                .preparedQuery("""
                UPDATE events
                SET sale_end_time = now()
                WHERE event_id = $1
                  AND status = 'SELLING'
            """)
                .execute(Tuple.of(eventId))
                .mapEmpty();
    }

    /* ================================
    GET ALL EVENTS (ADMIN)
    ================================ */
    public Future<List<Events>> getAllEvents() {
        return pool
                .preparedQuery("""
                SELECT
                    event_id,
                    event_name,
                    event_time,
                    status,
                    sale_start_time,
                    sale_open_at,
                    sale_end_time,
                    venue,
                    location
                FROM events
                ORDER BY event_id ASC
            """)
                .execute()
                .map(this::mapRows);
    }

    private List<Events> mapRows(RowSet<Row> rs) {
        List<Events> list = new ArrayList<>();
        for (Row row : rs) {
            list.add(Events.builder()
                    .eventId(row.getString("event_id"))
                    .eventName(row.getString("event_name"))
                    .eventTime(
                            row.getOffsetDateTime("event_time").toInstant()
                    )
                    .saleStartTime(
                            row.getOffsetDateTime("sale_start_time") != null
                                    ? row.getOffsetDateTime("sale_start_time").toInstant()
                                    : null
                    )
                    .saleOpenAt(
                            row.getOffsetDateTime("sale_open_at") != null
                                    ? row.getOffsetDateTime("sale_open_at").toInstant()
                                    : null
                    )
                    .saleEndTime(
                            row.getOffsetDateTime("sale_end_time") != null
                                    ? row.getOffsetDateTime("sale_end_time").toInstant()
                                    : null
                    )
                    .venue(row.getString("venue"))
                    .location(row.getString("location"))
                    .status(row.getString("status"))
                    .build());
        }
        return list;
    }

    public Future<Void> resetEventToLocked(String eventId) {
        return pool
                .preparedQuery("""
            UPDATE events
            SET
              sale_start_time = NULL,
              sale_open_at    = NULL,
              sale_end_time   = NULL,
              status          = 'LOCKED'
            WHERE event_id = $1
        """)
                .execute(Tuple.of(eventId))
                .mapEmpty();
    }
}
