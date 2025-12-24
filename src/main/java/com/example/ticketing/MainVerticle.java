package com.example.ticketing;

import com.example.ticketing.api.ApiError;
import com.example.ticketing.controller.AdminEventVerticle;
import com.example.ticketing.db.Db;
import com.example.ticketing.db.HikariPoolFactory;
import com.example.ticketing.job.BookingCleanupJob;
import com.example.ticketing.job.EventStatusJob;
import com.example.ticketing.job.ServeNextVerticle;
import com.example.ticketing.kafka.KafkaConsumerConfig;
import com.example.ticketing.kafka.KafkaProducerConfig;
import com.example.ticketing.repository.AdminEventRepository;
import com.example.ticketing.repository.EventRepository;
import com.example.ticketing.repository.PgEventRepository;
import com.example.ticketing.security.PoPIssueHandler;
import com.example.ticketing.security.PoPVerifier;
import io.vertx.config.*;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.redis.client.Redis;
import io.vertx.sqlclient.Pool;

public class MainVerticle extends AbstractVerticle {
    private KafkaConsumer<String, String> consumer;
    private KafkaProducer<String, String> producer;
    private Redis redis;

    @Override
    public void start(Promise<Void> startPromise) {

        ConfigRetriever retriever = ConfigRetriever.create(
                vertx,
                new ConfigRetrieverOptions()
                        .addStore(new ConfigStoreOptions()
                                .setType("file")
                                .setFormat("hocon")
                                .setConfig(new JsonObject().put("path", "application.conf"))
                        )
        );

        retriever.getConfig(ar -> {
            if (ar.failed()) {
                startPromise.fail(ar.cause());
                return;
            }

            JsonObject cfg = ar.result().getJsonObject("app");

            //
            Pool pool = PgPoolFactory.create(vertx, cfg.getJsonObject("postgres"));

            //
            JsonObject emqxCfg = cfg.getJsonObject("emqx");
            String emqxApiBase = emqxCfg.getString("apiBase");
            String emqxApiKey = emqxCfg.getString("apiKey");
            String emqxApiSecret = emqxCfg.getString("apiSecret");
            EmqxPublisher emqx = new EmqxPublisher(vertx, emqxApiBase, emqxApiKey, emqxApiSecret);

            // ===== DB =====
            Db db = new Db(pool, HikariPoolFactory.create(cfg.getJsonObject("postgres")));

            EventRepository repo = new PgEventRepository(pool);
            vertx.deployVerticle(new EventStatusJob(repo, emqx));





            // ===== Kafka Producer (ONE INSTANCE) =====
            producer = KafkaProducer.create(vertx, KafkaProducerConfig.from(cfg));
            //
            consumer = KafkaConsumer.create(vertx, KafkaConsumerConfig.from(cfg));
            //


            JsonObject redisCfg = cfg.getJsonObject("redis");
            String hostRedis = redisCfg.getString("host");
            Integer portRedis = redisCfg.getInteger("port");
            redis = RedisClientFactory.create(vertx,hostRedis,portRedis);

            // ===== JWT =====
            JWTAuth jwtAuth = createJwtAuth(vertx);

            //

            PoPVerifier popVerifier = new PoPVerifier(vertx, redis, emqxApiBase, emqxApiKey, emqxApiSecret);

            // ===== Router =====
            Router router = Router.router(vertx);
            router.route().handler(BodyHandler.create());
            router.route().handler(CorsHandler.create("*")
                    .allowedMethod(HttpMethod.POST)
                    .allowedMethod(HttpMethod.OPTIONS)
                    .allowedHeader("*"));
            router.route("/events/*")
                    .handler(JWTAuthHandler.create(jwtAuth));

            // router.post("/api/seat/*")
            //        .handler(new PopAuthHandler(popVerifier));

            // ===== HTTP handlers =====
            BookingVerticle booking = new BookingVerticle(db, redis);
            // ===== ROUTES =====
            router.get("/events/:eventId/sections")
                    .handler(booking::getSections);

            router.get("/events/:eventId/listings")
                    .handler(booking::getListings);

            router.post("/events/:eventId/hold")
                    .handler(booking::holdSeats);

            router.post("/events/:eventId/releaseHold")
                    .handler(booking::releaseHold);

            router.post("/events/:eventId/checkout")
                    .handler(booking::checkout);

            router.post("/events/payment/confirm")
                    .handler(booking::paymentConfirm);

            router.get("/events/booking/:id/tickets")
                    .handler(booking::getTickets);

            router.route("/getEvents");
            router.get("/getEvents")
                    .handler(booking::getEvents);
            router.route("/waiting/ready");
            router.post("/waiting/ready")
                    .handler(booking::waitingReady);


            router.get("/visitors/:visitorToken/holds")
                    .handler(booking::getVisitorHolds);

            router.route().failureHandler(ctx -> {
                Throwable err = ctx.failure();
                ApiError.system(ctx, err);
            });


            EnqueueVerticle enqueue = new EnqueueVerticle(db, producer, redis);
            router.route("/enqueue");
            router.post("/enqueue")
                    .handler(enqueue::handleEnqueue);

            PoPIssueHandler popIssueHandler = new PoPIssueHandler(redis);
            router.route("/api/pop/issue");
            router.post("/api/pop/issue")
                    .handler(popIssueHandler::issue);



            AdminEventRepository adminEventRepository = new AdminEventRepository(pool);
            AdminEventVerticle adminVerticle =
                    new AdminEventVerticle(adminEventRepository,redis);

            router.post("/admin/events/:eventId/actions/open-waiting")
                    .handler(adminVerticle::openWaiting);

            router.post("/admin/events/:eventId/actions/open-selling")
                    .handler(adminVerticle::openSelling);

            router.post("/admin/events/:eventId/actions/close-selling")
                    .handler(adminVerticle::closeSelling);
            router.post("/admin/events/:eventId/actions/reset-event")
                    .handler(adminVerticle::resetEvent);

            router.get("/admin/events")
                    .handler(adminVerticle::getEvents);


            int port = cfg
                    .getJsonObject("http")
                    .getInteger("port");

            vertx.createHttpServer()
                    .requestHandler(router)
                    .listen(port, http -> {
                        if (http.failed()) {
                            http.cause().printStackTrace();
                            startPromise.fail(http.cause());
                        } else {
                            System.out.println("✅ HTTP server started on port " + port);
                            startPromise.complete();
                        }
                    });

            vertx.deployVerticle(
                    new KafkaQueueConsumerVerticle(cfg, db, producer, consumer)
            );


            vertx.deployVerticle(
                    new ServeNextVerticle(db, redis, emqx, jwtAuth, producer)
            );
            vertx.deployVerticle(
                    new HoldExpiryVerticle(db)
            );
            vertx.deployVerticle(
                    new BookingCleanupJob(redis, db)
            );


        });
    }

    @Override
    public void stop() {
        if (producer != null) {
            producer.close();
        }
    }

    // ===== Helpers =====
    private JWTAuth createJwtAuth(Vertx vertx) {
        String secret = "my-very-secret-key";

        JWTAuthOptions options = new JWTAuthOptions()
                .addPubSecKey(new PubSecKeyOptions()
                        .setAlgorithm("HS256")
                        .setSecretKey(secret)
                        .setBuffer(Buffer.buffer(secret))
                );

        return JWTAuth.create(vertx, options);
    }
}
