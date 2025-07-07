package in.edu.kjc.configs;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;

public class MongoConfig {
    private static MongoClient mongoClient;

    public static MongoClient getClient(Vertx vertx) {
        if (mongoClient == null) {
            JsonObject config = new JsonObject()
                    .put("connection_string", "mongodb://localhost:27017")
                    .put("db_name", "todo_db");

            mongoClient = MongoClient.createShared(vertx, config);
        }
        return mongoClient;
    }
}
