package in.edu.kjc.configs;

import io.vertx.core.Vertx;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.RedisOptions;

public class RedisConfig {

    private static RedisAPI redisAPI;

    public static RedisAPI getRedisClient(Vertx vertx) {
        if (redisAPI == null) {
            Redis client = Redis.createClient(vertx, new RedisOptions()
                    .setConnectionString("redis://localhost:6379"));
            redisAPI = RedisAPI.api(client);
        }
        return redisAPI;
    }
}
