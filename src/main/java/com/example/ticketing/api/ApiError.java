package com.example.ticketing.api;

import io.vertx.ext.web.RoutingContext;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.HttpException;

public class ApiError {

    public static void business(
            RoutingContext ctx,
            ErrorCode code
    ) {
        ctx.response()
                .setStatusCode(code.httpStatus)
                .putHeader("content-type", "application/json")
                .end(new JsonObject()
                        .put("success", false)
                        .put("code", code.name())
                        .put("message", code.defaultMessage)
                        .encode()
                );
    }

    public static void system(
            RoutingContext ctx,
            Throwable err
    ) {
        if (err instanceof HttpException httpEx) {

            if (httpEx.getStatusCode() == 401 ||  httpEx.getStatusCode() == 403) {
                ctx.response()
                        .setStatusCode(httpEx.getStatusCode())
                        .putHeader("content-type", "application/json")
                        .end(new JsonObject()
                                .put("success", false)
                                .put("code", "JWR_ERROR")

                                .put("message",
                                        httpEx.getCause() != null
                                                ? httpEx.getCause().getMessage()
                                                : httpEx.getMessage()
                                )
                                .encode());
                return;
            }
        }
        ctx.response()
                .setStatusCode(500)
                .putHeader("content-type", "application/json")
                .end(new JsonObject()
                        .put("success", false)
                        .put("code", ErrorCode.INTERNAL_ERROR.name())
                        .put("message", ErrorCode.INTERNAL_ERROR.defaultMessage)
                        .encode()
                );

        err.printStackTrace(); // log
    }
}
