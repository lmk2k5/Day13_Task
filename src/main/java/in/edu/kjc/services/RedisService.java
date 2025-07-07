package in.edu.kjc.services;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.redis.client.Command;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisOptions;
import io.vertx.redis.client.Request;

public class RedisService {
    private final Redis client;

    public RedisService(Vertx vertx) {
        RedisOptions options = new RedisOptions()
                .setConnectionString("redis://localhost:6379");
        this.client = Redis.createClient(vertx, options);
    }

    public Future<Void> storeToken(String token, long expirySeconds) {
        return client.send(Request.cmd(Command.SETEX)
                        .arg(token)
                        .arg(expirySeconds)
                        .arg("valid"))
                .mapEmpty();
    }
    public Future<Void> storeToken(String token, String value, long expirySeconds) {
        return client.send(Request.cmd(Command.SETEX)
                        .arg(token)
                        .arg(expirySeconds)
                        .arg(value))
                .mapEmpty();
    }

    public Future<Boolean> isTokenValid(String token) {
        return client.send(Request.cmd(Command.GET).arg(token))
                .map(response -> response != null && "valid".equals(response.toString()));
    }

    public Future<Void> invalidateToken(String token) {
        return client.send(Request.cmd(Command.DEL).arg(token)).mapEmpty();
    }
    public Future<String> getTokenValue(String token) {
        return client.send(Request.cmd(Command.GET).arg(token))
                .map(response -> response == null ? null : response.toString());
    }

}
