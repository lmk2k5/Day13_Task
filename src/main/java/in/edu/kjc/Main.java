package in.edu.kjc;

import in.edu.kjc.handlers.AuthHandler;
import in.edu.kjc.handlers.TaskHandler;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import in.edu.kjc.services.MailService;

import java.util.Set;


import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.RedisOptions;
import java.util.List;


public class Main extends AbstractVerticle {

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new Main());
    }

    @Override
    public void start() {
        Router router = Router.router(vertx);
        router.route().handler(CorsHandler.create("*")
                .allowedHeaders(Set.of("Content-Type", "Authorization")));

        router.route().handler(BodyHandler.create());

        new AuthHandler(vertx).registerRoutes(router);
        new TaskHandler(vertx).registerRoutes(router);

        MailService mailService = new MailService(vertx);
        router.get("/api/test-email").handler(ctx -> {
            mailService.sendEmail("23iota10@kristujayanti.com", "Hello from Vert.x", "This is a test email.")
                    .onSuccess(v -> ctx.response().end("Email sent successfully"))
                    .onFailure(err -> ctx.response().setStatusCode(500).end("Email failed: " + err.getMessage()));
        });
        Redis redisClient = Redis.createClient(vertx, new RedisOptions().setConnectionString("redis://localhost:6379"));
        RedisAPI redis = RedisAPI.api(redisClient);

        router.get("/api/test-redis").handler(ctx -> {
            redis.set(List.of("testkey", "VertxRedisOK")).onSuccess(res -> {
                ctx.response().end("Redis is working: " + res.toString());
            }).onFailure(err -> {
                ctx.response().setStatusCode(500).end("Redis error: " + err.getMessage());
            });
        });

        vertx.createHttpServer()
                .requestHandler(router)
                .listen(8888, http -> {
                    if (http.succeeded()) {
                        System.out.println("HTTP server started on port 8888");
                    } else {
                        http.cause().printStackTrace();
                    }
                });
    }
}
