package com.example.ticketing.security;

import com.example.ticketing.db.Db;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

public class PopAuthHandler implements Handler<RoutingContext> {

    private final PoPVerifier popVerifier;

    public PopAuthHandler(PoPVerifier popVerifier) {
        this.popVerifier = popVerifier;
    }

    @Override
    public void handle(RoutingContext ctx) {

        String popToken = ctx.request().getHeader("X-PoP");
        if (popToken == null) {
            ctx.response().setStatusCode(401).end("POP_REQUIRED");
            return;
        }

        String visitorToken = ctx.user()
                .principal()
                .getString("visitorToken");

        popVerifier.verify(popToken, visitorToken)
                .onFailure(err ->
                        ctx.response().setStatusCode(401).end(err.getMessage())
                )
                .onSuccess(v -> ctx.next());
    }
}

