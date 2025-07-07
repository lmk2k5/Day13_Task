package in.edu.kjc.services;

import at.favre.lib.crypto.bcrypt.BCrypt;
import in.edu.kjc.configs.MongoConfig;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.mongo.MongoClient;

import java.util.UUID;

public class AuthService {
    private final MongoClient mongo;
    private final JWTAuth jwtProvider;
    private final MailService mailService;
    private final RedisService redisService;

    public Future<Void> completePasswordReset(String token, String newPassword) {
        return redisService.getTokenValue(token).compose(email -> {
            if (email == null) {
                return Future.failedFuture("Invalid or expired token");
            }

            String hashed = BCrypt.withDefaults().hashToString(12, newPassword.toCharArray());

            JsonObject query = new JsonObject().put("email", email);
            JsonObject update = new JsonObject().put("$set", new JsonObject().put("password", hashed));

            return mongo.updateCollection("users", query, update)
                    .compose(res -> redisService.invalidateToken(token))
                    .mapEmpty();
        });
    }

    public AuthService(Vertx vertx) {
        this.mongo = MongoConfig.getClient(vertx);
        this.jwtProvider = JWTAuth.create(vertx, new JWTAuthOptions()
                .addPubSecKey(new PubSecKeyOptions()
                        .setAlgorithm("HS256")
                        .setBuffer("secret")
                        .setSymmetric(true))
        );
        this.mailService = new MailService(vertx);
        this.redisService = new RedisService(vertx);
    }

    public Future<Void> registerUser(String email) {
        JsonObject query = new JsonObject().put("email", email);

        return mongo.findOne("users", query, null).compose(existingUser -> {
            if (existingUser != null) {
                return Future.failedFuture("User already exists");
            }

            String rawPassword = generateRandomPassword();
            String hashedPassword = BCrypt.withDefaults().hashToString(12, rawPassword.toCharArray());

            JsonObject user = new JsonObject()
                    .put("email", email)
                    .put("password", hashedPassword);

            return mailService.sendEmail(
                            email,
                            "Your To-Do App Password",
                            "Welcome!\nYour password: " + rawPassword
                    )
                    .compose(v -> mongo.insert("users", user))
                    .mapEmpty();
        });
    }

    public Future<String> authenticateUser(String email, String password) {
        JsonObject query = new JsonObject().put("email", email);

        return mongo.findOne("users", query, null).compose(user -> {
            if (user == null) {
                return Future.failedFuture("User not found");
            }

            String storedHash = user.getString("password");
            BCrypt.Result result = BCrypt.verifyer().verify(password.toCharArray(), storedHash);

            if (!result.verified) {
                return Future.failedFuture("Invalid password");
            }

            JsonObject claims = new JsonObject().put("email", email);
            JWTOptions options = new JWTOptions().setExpiresInMinutes(60);
            String token = jwtProvider.generateToken(claims, options);

            return redisService.storeToken(token, 3600).map(v -> token);
        });
    }

    public Future<Void> logout(String token) {
        return redisService.invalidateToken(token);
    }

    public JWTAuth getJwtProvider() {
        return jwtProvider;
    }

    public RedisService getRedisService() {
        return redisService;
    }

    public Future<Void> initiatePasswordReset(String email) {
        JsonObject query = new JsonObject().put("email", email);

        return mongo.findOne("users", query, null).compose(user -> {
            if (user == null) {
                return Future.failedFuture("User not found");
            }

            String token = UUID.randomUUID().toString();
            long expirySeconds = 900; // 15 minutes

            return redisService.storeToken(token, email, expirySeconds)
                    .compose(v -> mailService.sendEmail(
                            email,
                            "Password Reset Link",
                            "Click the link to reset your password:\n" +
                                    "http://localhost:8888/api/reset-password?token=" + token
                    ));
        });
    }

    private String generateRandomPassword() {
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            int idx = (int) (Math.random() * chars.length());
            sb.append(chars.charAt(idx));
        }
        return sb.toString();
    }
    public Future<String> refreshToken(String oldToken) {
        return redisService.isTokenValid(oldToken).compose(valid -> {
            if (!valid) {
                return Future.failedFuture("Token is invalid or expired");
            }

            return jwtProvider.authenticate(new JsonObject().put("token", oldToken)).compose(user -> {
                String email = user.principal().getString("email");

                if (email == null) {
                    return Future.failedFuture("Invalid token payload");
                }

                JsonObject claims = new JsonObject().put("email", email);
                JWTOptions options = new JWTOptions().setExpiresInMinutes(60);
                String newToken = jwtProvider.generateToken(claims, options);
                return redisService.storeToken(newToken, 3600)
                        .compose(v -> redisService.invalidateToken(oldToken))
                        .map(v -> newToken);
            });
        });
    }

}

