package com.example.ticketing.db;

import com.example.ticketing.model.Events;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.*;
import org.postgresql.copy.CopyManager;
import org.postgresql.core.BaseConnection;

import javax.sql.DataSource;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

public class Db {

    private final Pool pool;
    private final DataSource copyDs;

    public Db(Pool pool, DataSource copyDs) {
        this.pool = pool;
        this.copyDs = copyDs;
    }

    // ===================== PoP =====================

    public Future<Void> insertPopToken(String token, String visitor, int ttlSeconds) {
        return pool.withConnection(conn ->
                conn
                        .preparedQuery("""
                                    INSERT INTO channel_tokens(token, visitor_token, expires_at)
                                    VALUES ($1, $2, now() + ($3 || ' seconds')::interval)
                                """)
                        .execute(Tuple.of(token, visitor, ttlSeconds))
                        .mapEmpty()
        );
    }

    public Future<Boolean> consumePopToken(String token, String visitor) {
        return pool.withTransaction(conn ->
                conn
                        .preparedQuery("""
                                    UPDATE channel_tokens
                                    SET used = TRUE
                                    WHERE token = $1
                                      AND visitor_token = $2
                                      AND used = FALSE
                                      AND expires_at > now()
                                    RETURNING token
                                """)
                        .execute(Tuple.of(token, visitor))
                        .map(rs -> rs.rowCount() > 0)
        );
    }

    // ===================== Queue =====================

    public Future<Void> insertQueue(String eventId, String visitor) {
        return pool.withConnection(conn ->
                conn
                        .preparedQuery("""
                                    INSERT INTO event_queue(event_id, visitor_token, queue_ts, status)
                                    VALUES ($1, $2, now(), 'WAITING')
                                """)
                        .execute(Tuple.of(eventId, visitor))
                        .mapEmpty()
        );
    }

    // ===================== Seat Hold =====================

    public Future<Boolean> tryHoldSeat(
            String eventId,
            String seatId,
            String visitor,
            int holdMinutes
    ) {
        return pool.withTransaction(conn ->
                conn
                        .preparedQuery("""
                                    UPDATE seat_inventory
                                    SET seat_status = 'DANG_GIU',
                                        user_id = $3,
                                        hold_expiry = now() + ($4 * interval '1 minute')
                                    WHERE event_id = $1
                                      AND seat_id = $2
                                      AND seat_status = 'TRONG'
                                    RETURNING seat_id
                                """)
                        .execute(Tuple.of(eventId, seatId, visitor, holdMinutes))
                        .map(rs -> rs.rowCount() > 0)
        );
    }

    public Future<Void> releaseSeat(String eventId, String seatId, String visitor) {
        return pool.withConnection(conn ->
                conn
                        .preparedQuery("""
                                    UPDATE seat_inventory
                                    SET seat_status = 'TRONG',
                                        user_id = NULL,
                                        hold_expiry = NULL
                                    WHERE event_id = $1
                                      AND seat_id = $2
                                      AND user_id = $3
                                """)
                        .execute(Tuple.of(eventId, seatId, visitor))
                        .mapEmpty()
        );
    }

    public void copyQueueBatch(List<JsonObject> batch) throws Exception {
        String csv = buildCsv(batch);
        try (Connection conn = copyDs.getConnection()) {
            CopyManager copyManager = new CopyManager((BaseConnection) conn.unwrap(org.postgresql.core.BaseConnection.class));
            String copySql = "COPY event_queue(visitor_token,event_id,queue_timestamp,status) FROM STDIN WITH (FORMAT csv, HEADER false)";
            StringReader reader = new StringReader(csv);
            long rows = copyManager.copyIn(copySql, reader);
            System.out.println("COPY inserted rows: " + rows);
        }
    }

    private String buildCsv(List<JsonObject> batch) {
        StringBuilder sb = new StringBuilder(batch.size() * 128);

        for (JsonObject e : batch) {
            sb.append('"').append(csv(e.getString("visitorToken"))).append('"').append(',')
                    .append('"').append(csv(e.getString("eventId"))).append('"').append(',')
                    .append('"').append(csv(e.getString("queueTimestamp"))).append('"').append(',')
                    .append("\"WAITING\"")
                    .append('\n');
        }

        return sb.toString();
    }

    private static String csv(String v) {
        if (v == null) return "";
        return v.replace("\"", "\"\"");
    }

    public Future<Events> loadEvent(String eventId) {
        return pool.withConnection(conn ->
                        conn.preparedQuery("""
                                    SELECT
                                        event_id,
                                        event_name,
                                        event_time,
                                        sale_start_time,
                                        sale_end_time,
                                        sale_open_at,
                                        status
                                    FROM events
                                    WHERE event_id = $1
                                """).execute(Tuple.of(eventId)))
                .map(rs -> {
                    if (rs.rowCount() == 0) {
                        return null;
                    }
                    Row row = rs.iterator().next();
                    return new Events(
                            row.getString("event_id"),
                            row.getString("event_name"),
                            row.getOffsetDateTime("event_time") != null
                                    ? row.getOffsetDateTime("event_time").toInstant()
                                    : null,
                            row.getOffsetDateTime("sale_start_time") != null
                                    ? row.getOffsetDateTime("sale_start_time").toInstant()
                                    : null,
                            row.getOffsetDateTime("sale_end_time") != null
                                    ? row.getOffsetDateTime("sale_end_time").toInstant()
                                    : null,
                            row.getOffsetDateTime("sale_open_at") != null
                                    ? row.getOffsetDateTime("sale_open_at").toInstant()
                                    : null,
                            row.getString("status")
                    );
                });

    }

    public Future<List<Events>> loadActiveEvents() {
        return pool.withConnection(conn ->
                conn.preparedQuery("""
                                    SELECT event_id, event_name, sale_start_time, sale_end_time, status
                                    FROM events
                                    WHERE status = 'DANG_BAN'
                                """).execute()
                        .map(rs -> {
                            List<Events> list = new ArrayList<>();
                            for (Row row : rs) {
                                list.add(Events.builder()
                                        .eventId(row.getString("event_id"))
                                        .saleStartTime(row.getOffsetDateTime("sale_start_time").toInstant())
                                        .saleEndTime(row.getOffsetDateTime("sale_end_time").toInstant())
                                        .status(row.getString("status"))
                                        .build());
                            }
                            return list;
                        })
        );
    }

    public void updateServedBatch(List<JsonObject> batch) throws Exception {
        try (Connection conn = copyDs.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement("""
                        UPDATE event_queue
                        SET status = 'SERVED',
                            access_token = ?,
                            served_at = to_timestamp(? / 1000.0)
                        WHERE event_id = ?
                          AND visitor_token = ?
                    """)) {
                for (JsonObject e : batch) {
                    ps.setString(1, e.getString("accessToken"));
                    ps.setLong(2, e.getLong("servedAt"));
                    ps.setString(3, e.getString("eventId"));
                    ps.setString(4, e.getString("visitorToken"));
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            conn.commit();
        }
    }

    private static final String SQL_FIND_ACTIVE_HOLD = """
                SELECT
                  h.hold_token,
                  h.expires_at,
                  s.section_id,
                  s.row_id,
                  s.seat_number,
                  s.price
                FROM holds h
                JOIN seats s
                  ON s.hold_token = h.hold_token
                WHERE h.event_id = $1
                  AND h.section_id = $2
                  AND h.visitor_token = $3
                  AND h.status = 'ACTIVE'
                  AND h.expires_at > now()
                ORDER BY s.row_id, s.seat_number
            """;

    public Future<JsonObject> holdSeats(
            String eventId,
            String visitorToken,
            String sectionId,
            int quantity,
            int price,
            boolean adjacent,
            int ttlSeconds
    ) {
        Promise<JsonObject> promise = Promise.promise();

        pool.getConnection(connAr -> {
            if (connAr.failed()) {
                promise.fail(connAr.cause());
                return;
            }

            SqlConnection conn = connAr.result();

            // ===== 1️⃣ CHECK ACTIVE HOLD (IDEMPOTENT) =====
            conn.preparedQuery(SQL_FIND_ACTIVE_HOLD)
                    .execute(Tuple.of(eventId, sectionId, visitorToken))
                    .onSuccess(existing -> {

                        if (existing.size() > 0) {
                            // 👉 StrictMode / retry vào đây
                            JsonArray seats = new JsonArray();
                            int totalPrice = 0;
                            String holdToken = null;

                            for (Row r : existing) {
                                holdToken = r.getString("hold_token");

                                seats.add(new JsonObject()
                                        .put("seatId",
                                                r.getString("section_id") + "-"
                                                        + r.getString("row_id") + "-"
                                                        + r.getInteger("seat_number"))
                                        .put("sectionId", r.getString("section_id"))
                                        .put("row", r.getString("row_id"))
                                        .put("number", r.getInteger("seat_number"))
                                        .put("price", r.getInteger("price"))
                                );

                                totalPrice += r.getInteger("price");
                            }

                            conn.close();
                            promise.complete(new JsonObject()
                                    .put("holdToken", holdToken)
                                    .put("eventId", eventId)
                                    .put("sectionId", sectionId)
                                    .put("visitorToken", visitorToken)
                                    .put("quantity", seats.size())
                                    .put("adjacent", adjacent)
                                    .put("price", totalPrice)
                                    .put("expiresIn", ttlSeconds)
                                    .put("seats", seats)
                                    .put("duplicate", true)
                            );
                            return;
                        }

                        // ===== 2️⃣ KHÔNG CÓ HOLD → TẠO HOLD MỚI =====
                        createHold(
                                conn,
                                eventId,
                                visitorToken,
                                sectionId,
                                quantity,
                                price,
                                adjacent,
                                ttlSeconds,
                                promise
                        );
                    })
                    .onFailure(err -> {
                        conn.close();
                        promise.fail(err);
                    });
        });

        return promise.future();
    }


    // ===== HOLD ADJACENT (ghế liền nhau) =====
    public static final String SQL_HOLD_ADJACENT = """
            
             WITH available AS (
                SELECT
                    event_id,
                    section_id,
                    row_id,
                    seat_number,
                    price,
                    seat_number
                      - ROW_NUMBER() OVER (
                          PARTITION BY event_id, section_id, row_id
                          ORDER BY seat_number
                      ) AS grp
                FROM seats
                WHERE event_id = $3
                  AND section_id = $4
                  AND seat_status = 'TRONG'
            ),
            
            windows AS (
                SELECT
                    event_id,
                    section_id,
                    row_id,
                    grp,
                    seat_number,
                    price,
                    COUNT(*) OVER w AS window_len,
                    SUM(price) OVER w AS total_price,
                    ROW_NUMBER() OVER (
                        PARTITION BY event_id, section_id, row_id, grp
                        ORDER BY seat_number
                    ) AS pos
                FROM available
                WINDOW w AS (
                    PARTITION BY event_id, section_id, row_id, grp
                    ORDER BY seat_number
                    ROWS BETWEEN CURRENT ROW AND ($5 - 1) FOLLOWING
                )
            ),
            
            picked AS (
                SELECT
                    event_id,
                    section_id,
                    row_id,
                    grp,
                    MIN(pos) AS start_pos
                FROM windows
                WHERE window_len = $5
                  AND total_price = $6
                GROUP BY event_id, section_id, row_id, grp
                LIMIT 1
            ),
            
            seat_list AS (
                SELECT
                    w.event_id,
                    w.section_id,
                    w.row_id,
                    ARRAY_AGG(w.seat_number ORDER BY w.seat_number) AS seat_numbers
                FROM windows w
                JOIN picked p
                  ON w.event_id   = p.event_id
                 AND w.section_id = p.section_id
                 AND w.row_id     = p.row_id
                 AND w.grp        = p.grp
                 AND w.pos BETWEEN p.start_pos AND p.start_pos + $5 - 1
                GROUP BY w.event_id, w.section_id, w.row_id
            )
            
            UPDATE seats s
            SET seat_status = 'DANG_GIU',
                hold_token  = $1,
                hold_expiry = now() + make_interval(secs => $2)
            FROM seat_list l
            WHERE s.event_id    = l.event_id
              AND s.section_id  = l.section_id
              AND s.row_id      = l.row_id
              AND s.seat_number = ANY (l.seat_numbers)
            RETURNING
              s.section_id,
              s.row_id,
              s.seat_number,
              s.price;
            """;

    // ===== HOLD NON-ADJACENT (không cần liền nhau) =====
    public static final String SQL_HOLD_NON_ADJACENT = """
            WITH picked AS (
              SELECT
                event_id,
                section_id,
                row_id,
                seat_number
              FROM seats
              WHERE event_id = $3
                AND section_id = $4
                AND seat_status = 'TRONG'
              ORDER BY price ASC
              LIMIT $5
            )
            UPDATE seats s
            SET seat_status = 'DANG_GIU',
                hold_token = $1,
                hold_expiry = now() + make_interval(secs => $2)
            FROM picked p
            WHERE s.event_id    = p.event_id
              AND s.section_id  = p.section_id
              AND s.row_id      = p.row_id
              AND s.seat_number = p.seat_number
            RETURNING
              s.section_id,
              s.row_id,
              s.seat_number,
              s.price;
            """;

    private void createHold(
            SqlConnection conn,
            String eventId,
            String visitorToken,
            String sectionId,
            int quantity,
            int price,
            boolean adjacent,
            int ttlSeconds,
            Promise<JsonObject> promise
    ) {
        String holdToken = "HOLD_" + UUID.randomUUID();

        conn.begin(txAr -> {
            if (txAr.failed()) {
                conn.close();
                promise.fail(txAr.cause());
                return;
            }

            Transaction tx = txAr.result();

            // 1️⃣ INSERT HOLD
            conn.preparedQuery("""
                                INSERT INTO holds
                                  (hold_token, event_id, section_id, visitor_token,
                                   quantity, status, expires_at)
                                VALUES ($1, $2, $3, $4, $5, 'ACTIVE',
                                        now() + make_interval(secs => $6))
                            """)
                    .execute(Tuple.of(
                            holdToken,
                            eventId,
                            sectionId,
                            visitorToken,
                            quantity,
                            ttlSeconds
                    ))
                    .compose(v -> {

                        String sql = adjacent
                                ? SQL_HOLD_ADJACENT
                                : SQL_HOLD_NON_ADJACENT;

                        Tuple params = adjacent
                                ? Tuple.of(
                                holdToken,
                                ttlSeconds,
                                eventId,
                                sectionId,
                                quantity,
                                price
                        )
                                : Tuple.of(
                                holdToken,
                                ttlSeconds,
                                eventId,
                                sectionId,
                                quantity
                        );

                        return conn.preparedQuery(sql).execute(params);
                    })
                    .onSuccess(rs -> {

                        if (rs.rowCount() != quantity) {
                            tx.rollback(v -> {
                                conn.close();
                                promise.fail(adjacent
                                        ? "NO_ADJACENT_SEATS"
                                        : "INSUFFICIENT_SEATS"
                                );
                            });
                            return;
                        }

                        JsonArray seats = new JsonArray();
                        int totalPrice = 0;

                        for (Row r : rs) {
                            seats.add(new JsonObject()
                                    .put("seatId",
                                            r.getString("section_id") + "-"
                                                    + r.getString("row_id") + "-"
                                                    + r.getInteger("seat_number"))
                                    .put("sectionId", r.getString("section_id"))
                                    .put("row", r.getString("row_id"))
                                    .put("number", r.getInteger("seat_number"))
                                    .put("price", r.getInteger("price"))
                            );
                            totalPrice += r.getInteger("price");
                        }

                        int finalTotalPrice = totalPrice;
                        tx.commit(v -> {
                            conn.close();
                            promise.complete(new JsonObject()
                                    .put("holdToken", holdToken)
                                    .put("eventId", eventId)
                                    .put("sectionId", sectionId)
                                    .put("visitorToken", visitorToken)
                                    .put("quantity", quantity)
                                    .put("adjacent", adjacent)
                                    .put("price", finalTotalPrice)
                                    .put("expiresIn", ttlSeconds)
                                    .put("seats", seats)
                                    .put("duplicate", false)
                            );
                        });
                    })
                    .onFailure(err -> {
                        tx.rollback(v -> {
                            conn.close();
                            promise.fail(err);
                        });
                    });
        });
    }


    String SQL_LISTINGS_ADJACENT = """
            
                     WITH available AS (
                SELECT
                    section_id,
                    row_id,
                    seat_number,
                    price,
                    seat_number
                    - ROW_NUMBER() OVER (
                        PARTITION BY section_id, row_id
                        ORDER BY seat_number
                    ) AS grp
                FROM seats
                WHERE event_id = $1
                  AND seat_status = 'TRONG'
            ),
            
            sub_blocks AS (
                SELECT
                    a.section_id,
                    COUNT(*) OVER w AS window_len,
                    SUM(a.price) OVER w AS total_price
                FROM available a
                WINDOW w AS (
                    PARTITION BY section_id, row_id, grp
                    ORDER BY seat_number
                    ROWS BETWEEN CURRENT ROW AND ($2 - 1) FOLLOWING
                )
            )
            
            SELECT
                section_id,
                total_price AS price,
                COUNT(*) AS block_count
            FROM sub_blocks
            WHERE window_len = $2      -- 👈 CHẶN CHUẨN
            GROUP BY section_id, total_price
            ORDER BY section_id, price;
            """;
    String SQL_LISTINGS_NON_ADJACENT = """
            WITH ranked AS (
              SELECT
                section_id,
                price,
                ROW_NUMBER()
                  OVER (PARTITION BY section_id ORDER BY price ASC) AS rn
              FROM seats
              WHERE event_id = $1
                AND seat_status = 'TRONG'
            )
            SELECT
              section_id,
              SUM(price) AS price,
              1 AS block_count
            FROM ranked
            WHERE rn <= $2
            GROUP BY section_id;
            """;

    public Future<JsonArray> fetchListingsBySection(
            String eventId,
            int quantity,
            boolean adjacent
    ) {
        Promise<JsonArray> promise = Promise.promise();

        String sql = adjacent
                ? SQL_LISTINGS_ADJACENT
                : SQL_LISTINGS_NON_ADJACENT;

        pool.getConnection(ar -> {
            if (ar.failed()) {
                promise.fail(ar.cause());
                return;
            }

            SqlConnection conn = ar.result();

            conn.preparedQuery(sql)
                    .execute(Tuple.of(eventId, quantity))
                    .onSuccess(rs -> {

                        Map<String, JsonObject> sectionMap = new LinkedHashMap<>();

                        for (Row r : rs) {
                            String sectionId = r.getString("section_id");

                            JsonObject section = sectionMap.computeIfAbsent(
                                    sectionId,
                                    k -> new JsonObject()
                                            .put("sectionId", sectionId)
                                            .put("available", true)
                                            .put("priceOptions", new JsonArray())
                            );

                            section.getJsonArray("priceOptions").add(
                                    new JsonObject()
                                            .put("price", r.getInteger("price"))
                                            .put("blockCount", r.getInteger("block_count"))
                            );
                        }

                        conn.close();
                        promise.complete(new JsonArray(new ArrayList<>(sectionMap.values())));
                    })
                    .onFailure(err -> {
                        conn.close();
                        promise.fail(err);
                    });
        });

        return promise.future();
    }


    public Future<JsonArray> fetchSections(String eventId) {
        Promise<JsonArray> promise = Promise.promise();
        pool.getConnection(ar -> {
            if (ar.failed()) {
                promise.fail(ar.cause());
                return;
            }

            SqlConnection conn = ar.result();
            conn
                    .preparedQuery("""
                              SELECT
                                section_id,
                                MIN(price) AS min_price,
                                COUNT(*) FILTER (WHERE seat_status='TRONG') AS available
                              FROM seats
                              WHERE event_id = $1
                              GROUP BY section_id
                              ORDER BY section_id
                            """)
                    .execute(Tuple.of(eventId))
                    .onSuccess(rs -> {
                        JsonArray arr = new JsonArray();
                        for (Row row : rs) {
                            arr.add(new JsonObject()
                                    .put("sectionId", row.getString("section_id"))
                                    .put("minPrice", row.getInteger("min_price"))
                                    .put("available", row.getLong("available"))
                            );
                        }
                        conn.close();
                        promise.complete(arr);
                    })
                    .onFailure(err -> {
                        conn.close();
                        promise.fail(err);
                    });
        });

        return promise.future();
    }


    public Future<JsonArray> getActiveHoldsByVisitor(
            String eventId,
            String visitorToken
    ) {
        return pool
                .preparedQuery("""
                          SELECT hold_token, seat_ids, expires_at
                          FROM holds
                          WHERE event_id = $1
                            AND visitor_token = $2
                            AND status = 'ACTIVE'
                        """)
                .execute(Tuple.of(eventId, visitorToken))
                .map(rs -> {
                    JsonArray arr = new JsonArray();
                    for (Row r : rs) {
                        arr.add(new JsonObject()
                                .put("holdToken", r.getString("hold_token"))
                                .put("seatIds", new JsonArray(Arrays.asList(r.getArrayOfStrings("seat_ids"))))
                                .put("expiresAt", r.getOffsetDateTime("expires_at").toString())
                        );
                    }
                    return arr;
                });
    }

    public Future<Void> releaseHold(String holdToken, String visitorToken) {
        Promise<Void> promise = Promise.promise();

        pool.getConnection(ar -> {
            if (ar.failed()) {
                promise.fail(ar.cause());
                return;
            }

            SqlConnection conn = ar.result();

            conn.begin(txAr -> {
                if (txAr.failed()) {
                    conn.close();
                    promise.fail(txAr.cause());
                    return;
                }

                Transaction tx = txAr.result();

                // ===== 1️⃣ LOCK HOLD (idempotent) =====
                conn.preparedQuery("""
                                    SELECT status
                                    FROM holds
                                    WHERE hold_token = $1
                                      AND visitor_token = $2
                                    FOR UPDATE
                                """)
                        .execute(Tuple.of(holdToken, visitorToken))
                        .compose(rs -> {

                            if (rs.size() == 0) {
                                // ❌ hold không thuộc visitor
                                return Future.failedFuture("HOLD_NOT_FOUND_OR_FORBIDDEN");
                            }

                            String status = rs.iterator().next().getString("status");

                            // ✅ ĐÃ RELEASE / CONFIRMED → coi như OK
                            if (!"ACTIVE".equals(status)) {
                                return Future.succeededFuture();
                            }

                            // ===== 2️⃣ MARK HOLD EXPIRED =====
                            return conn.preparedQuery("""
                                                UPDATE holds
                                                SET status = 'EXPIRED'
                                                WHERE hold_token = $1
                                            """)
                                    .execute(Tuple.of(holdToken))
                                    .compose(v ->

                                            // ===== 3️⃣ RELEASE SEATS (idempotent) =====
                                            conn.preparedQuery("""
                                                        UPDATE seats
                                                        SET seat_status = 'TRONG',
                                                            hold_token = NULL,
                                                            hold_expiry = NULL
                                                        WHERE hold_token = $1
                                                    """).execute(Tuple.of(holdToken))
                                    );
                        })
                        .onSuccess(v -> {
                            tx.commit(x -> {
                                conn.close();
                                promise.complete();
                            });
                        })
                        .onFailure(err -> {
                            tx.rollback(x -> {
                                conn.close();
                                promise.fail(err);
                            });
                        });
            });
        });

        return promise.future();
    }

    public void expireHolds() {

        pool.getConnection(ar -> {
            if (ar.failed()) return;

            SqlConnection conn = ar.result();

            conn.begin(txAr -> {
                if (txAr.failed()) {
                    conn.close();
                    return;
                }

                Transaction tx = txAr.result();

                conn.query("""
                                    WITH expired AS (
                                      SELECT hold_token
                                      FROM holds
                                      WHERE status = 'ACTIVE'
                                        AND expires_at < now()
                                    ),
                                    released AS (
                                      UPDATE seats
                                      SET seat_status = 'TRONG',
                                          hold_token = NULL,
                                          hold_expiry = NULL
                                      WHERE hold_token IN (SELECT hold_token FROM expired)
                                      RETURNING hold_token
                                    )
                                    UPDATE holds
                                    SET status = 'EXPIRED'
                                    WHERE hold_token IN (SELECT hold_token FROM expired);
                                """)
                        .execute()
                        .onSuccess(v -> {
                            tx.commit(x -> conn.close());
                        })
                        .onFailure(err -> {
                            tx.rollback(x -> conn.close());
                        });
            });
        });
    }

    private static final String SQL_FIND_BOOKING_BY_HOLD = """
                SELECT
                  b.booking_id,
                  b.payment_status
                FROM bookings b
                WHERE b.hold_token = $1
            """;
    String SQL_CHECKOUT_WITH_BOOKING = """
                WITH valid_hold AS (
                  SELECT
                    h.hold_token,
                    h.event_id
                  FROM holds h
                  WHERE h.hold_token = $1
                    AND h.visitor_token = $2
                    AND h.status = 'ACTIVE'
                    AND h.expires_at > now()
                ),
                sold_seats AS (
                  UPDATE seats s
                  SET seat_status = 'DA_BAN',
                      hold_token = NULL,
                      hold_expiry = NULL
                  WHERE s.hold_token IN (SELECT hold_token FROM valid_hold)
                  RETURNING
                    s.section_id,
                    s.row_id,
                    s.seat_number,
                    s.price
                ),
                insert_booking AS (
                  INSERT INTO bookings (
                    booking_id,
                    hold_token,
                    event_id,
                    seat_ids,
                    total_amount,
                    payment_status
                  )
                  SELECT
                    $3 AS booking_id,
                    vh.hold_token,
                    vh.event_id,
                    ARRAY_AGG(
                      ss.section_id || '-' || ss.row_id || '-' || ss.seat_number
                      ORDER BY ss.section_id, ss.row_id, ss.seat_number
                    ),
                    SUM(ss.price),
                    'PENDING'
                  FROM sold_seats ss
                  JOIN valid_hold vh ON true
                  GROUP BY vh.hold_token, vh.event_id
                  RETURNING *
                )
                UPDATE holds
                SET status = 'CONFIRMED'
                WHERE hold_token IN (SELECT hold_token FROM valid_hold)
                RETURNING
                  (SELECT booking_id FROM insert_booking) AS booking_id;
            """;

    public Future<JsonObject> checkoutHold(
            String holdToken,
            String visitorToken
    ) {
        Promise<JsonObject> promise = Promise.promise();

        pool.getConnection(ar -> {
            if (ar.failed()) {
                promise.fail(ar.cause());
                return;
            }

            SqlConnection conn = ar.result();

            conn.begin(txAr -> {
                if (txAr.failed()) {
                    conn.close();
                    promise.fail(txAr.cause());
                    return;
                }

                Transaction tx = txAr.result();

                // ===== 1️⃣ CHECK EXISTING BOOKING (IDEMPOTENT) =====
                conn.preparedQuery(SQL_FIND_BOOKING_BY_HOLD)
                        .execute(Tuple.of(holdToken))
                        .compose(existing -> {

                            if (existing.size() > 0) {
                                Row r = existing.iterator().next();

                                // 👉 checkout đã xong trước đó
                                return Future.succeededFuture(
                                        new JsonObject()
                                                .put("bookingId", r.getString("booking_id"))
                                                .put("holdToken", holdToken)
                                                .put("status", "CONFIRMED")
                                                .put("paymentStatus", r.getString("payment_status"))
                                                .put("duplicate", true)
                                );
                            }

                            // ===== 2️⃣ CHƯA CÓ → CHECKOUT THẬT =====
                            String bookingId = "BKG_" + UUID.randomUUID();

                            return conn.preparedQuery(SQL_CHECKOUT_WITH_BOOKING)
                                    .execute(Tuple.of(
                                            holdToken,
                                            visitorToken,
                                            bookingId
                                    ))
                                    .compose(rs -> {

                                        if (rs.rowCount() == 0) {
                                            return Future.failedFuture("CHECKOUT_NOT_ALLOWED");
                                        }

                                        Row row = rs.iterator().next();

                                        return Future.succeededFuture(
                                                new JsonObject()
                                                        .put("bookingId", row.getString("booking_id"))
                                                        .put("holdToken", holdToken)
                                                        .put("status", "CONFIRMED")
                                                        .put("paymentStatus", "PENDING")
                                                        .put("duplicate", false)
                                        );
                                    });
                        })
                        .onSuccess(result -> {
                            tx.commit(v -> {
                                conn.close();
                                promise.complete(result);
                            });
                        })
                        .onFailure(err -> {
                            tx.rollback(v -> {
                                conn.close();
                                promise.fail(err);
                            });
                        });
            });
        });

        return promise.future();
    }

    String SQL_PAYMENT_CONFIRM = """
            WITH booking_ok AS (
              SELECT *
              FROM bookings
              WHERE booking_id = $1
                AND payment_status = 'PENDING'
            ),
            mark_paid AS (
              UPDATE bookings
              SET payment_status = 'SUCCESS'
              WHERE booking_id IN (SELECT booking_id FROM booking_ok)
              RETURNING booking_id, event_id, seat_ids
            ),
            issue_tickets AS (
              INSERT INTO tickets (
                ticket_id,
                booking_id,
                event_id,
                seat_id,
                qr_code,
                status
              )
              SELECT
                'TCK_' || gen_random_uuid(),
                mp.booking_id,
                mp.event_id,
                seat_id,
                encode(digest(mp.booking_id || ':' || seat_id, 'sha256'), 'hex'),
                'ACTIVE'
              FROM mark_paid mp,
                   UNNEST(mp.seat_ids) AS seat_id
              ON CONFLICT DO NOTHING
              RETURNING *
            )
              SELECT
                mp.booking_id,
                mp.event_id,
                COUNT(it.booking_id) AS issued_count
              FROM mark_paid mp
              LEFT JOIN issue_tickets it
                ON it.booking_id = mp.booking_id
              GROUP BY mp.booking_id, mp.event_id;  
            """;
    private static final String SQL_GET_BOOKING_STATUS = """
                SELECT
                  booking_id,
                  event_id,
                  payment_status
                FROM bookings
                WHERE booking_id = $1
            """;

    public Future<JsonObject> confirmPayment(String bookingId) {

        Promise<JsonObject> promise = Promise.promise();

        pool.getConnection(ar -> {
            if (ar.failed()) {
                promise.fail(ar.cause());
                return;
            }

            SqlConnection conn = ar.result();

            conn.begin(txAr -> {
                if (txAr.failed()) {
                    conn.close();
                    promise.fail(txAr.cause());
                    return;
                }

                Transaction tx = txAr.result();

                // ===== 1️⃣ TRY CONFIRM PAYMENT (LẦN ĐẦU) =====
                conn.preparedQuery(SQL_PAYMENT_CONFIRM)
                        .execute(Tuple.of(bookingId))
                        .compose(rs -> {

                            if (rs.rowCount() > 0) {
                                // ✅ LẦN 1: vừa confirm xong
                                Row r = rs.iterator().next();

                                return Future.succeededFuture(
                                        new JsonObject()
                                                .put("bookingId", r.getString("booking_id"))
                                                .put("ticketsIssued", r.getInteger("issued_count"))
                                                .put("paymentStatus", "SUCCESS")
                                                .put("event_id", r.getString("event_id"))
                                                .put("duplicate", false)
                                );
                            }

                            // ===== 2️⃣ ĐÃ CONFIRM TRƯỚC ĐÓ → LẤY STATE CUỐI =====
                            return conn.preparedQuery(SQL_GET_BOOKING_STATUS)
                                    .execute(Tuple.of(bookingId))
                                    .compose(bs -> {

                                        if (bs.size() == 0) {
                                            return Future.failedFuture("BOOKING_NOT_FOUND");
                                        }

                                        Row b = bs.iterator().next();

                                        if (!"SUCCESS".equals(b.getString("payment_status"))) {
                                            // booking không ở trạng thái hợp lệ
                                            return Future.failedFuture("PAYMENT_NOT_ALLOWED");
                                        }

                                        return Future.succeededFuture(
                                                new JsonObject()
                                                        .put("bookingId", b.getString("booking_id"))
                                                        .put("ticketsIssued", 0) // đã issue trước đó
                                                        .put("paymentStatus", "SUCCESS")
                                                        .put("event_id", b.getString("event_id"))
                                                        .put("duplicate", true)
                                        );
                                    });
                        })
                        .onSuccess(result -> {
                            tx.commit(v -> {
                                conn.close();
                                promise.complete(result);
                            });
                        })
                        .onFailure(err -> {
                            tx.rollback(v -> {
                                conn.close();
                                promise.fail(err);
                            });
                        });
            });
        });

        return promise.future();
    }


    String SQL_GET_TICKETS_BY_BOOKING = """
            SELECT
              t.ticket_id,
              t.seat_id,
              t.qr_code,
              t.status,
              t.issued_at,
              b.event_id
            FROM tickets t
            JOIN bookings b ON b.booking_id = t.booking_id
            JOIN holds h ON h.hold_token = b.hold_token
            WHERE b.booking_id = $1
              AND h.visitor_token = $2
              AND b.payment_status = 'SUCCESS'
            ORDER BY t.seat_id;
            
            """;

    public Future<JsonArray> getTicketsByBooking(
            String bookingId,
            String visitorToken
    ) {
        Promise<JsonArray> promise = Promise.promise();

        pool.getConnection(ar -> {
            if (ar.failed()) {
                promise.fail(ar.cause());
                return;
            }

            SqlConnection conn = ar.result();

            conn.preparedQuery(SQL_GET_TICKETS_BY_BOOKING)
                    .execute(Tuple.of(bookingId, visitorToken))
                    .onSuccess(rs -> {

                        if (!rs.iterator().hasNext()) {
                            conn.close();
                            promise.fail("TICKETS_NOT_AVAILABLE");
                            return;
                        }

                        JsonArray tickets = new JsonArray();

                        for (Row r : rs) {
                            tickets.add(new JsonObject()
                                    .put("ticketId", r.getString("ticket_id"))
                                    .put("eventId", r.getString("event_id"))
                                    .put("seatId", r.getString("seat_id"))
                                    .put("qrCode", r.getString("qr_code"))
                                    .put("status", r.getString("status"))
                                    .put("issuedAt", r.getOffsetDateTime("issued_at").toString())
                            );
                        }

                        conn.close();
                        promise.complete(tickets);
                    })
                    .onFailure(err -> {
                        conn.close();
                        promise.fail(err);
                    });
        });

        return promise.future();
    }

    public Future<List<String>> loadEventsForCleanup(Instant notBefore) {

        Promise<List<String>> promise = Promise.promise();

        pool.getConnection(ar -> {
            if (ar.failed()) {
                promise.fail(ar.cause());
                return;
            }

            SqlConnection conn = ar.result();

            String sql = """
                        SELECT event_id
                        FROM events
                        WHERE sale_end_time IS NULL OR sale_end_time >= $1
                    """;

            // 🔥 FIX: Instant → OffsetDateTime
            OffsetDateTime notBeforeTs =
                    notBefore.atOffset(ZoneOffset.ofHours(7));

            conn.preparedQuery(sql)
                    .execute(Tuple.of(notBeforeTs))
                    .onSuccess(rs -> {

                        List<String> eventIds = new ArrayList<>();

                        for (Row r : rs) {
                            eventIds.add(r.getString("event_id"));
                        }

                        conn.close();
                        promise.complete(eventIds);
                    })
                    .onFailure(err -> {
                        conn.close();
                        promise.fail(err);
                    });
        });

        return promise.future();
    }

    public Future<List<Events>> getEvents() {
        return pool
                .preparedQuery("""
            SELECT
                event_id,
                event_name,
                event_time,
                sale_start_time,
                sale_open_at,
                sale_end_time,
                venue,
                location,
                status
            FROM events
            WHERE status <> 'ENDED'
            ORDER BY event_time ASC
        """)
                .execute()
                .map(rs -> {
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
                });
    }





}
