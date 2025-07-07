package in.edu.kjc.handlers;

import in.edu.kjc.services.AuthService;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class AuthHandler {

    private final AuthService authService;
    private void handleCompletePasswordReset(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        String token = body.getString("token");
        String newPassword = body.getString("newPassword");

        if (token == null || newPassword == null) {
            ctx.response().setStatusCode(400).end("Missing token or new password");
            return;
        }

        authService.completePasswordReset(token, newPassword)
                .onSuccess(v -> ctx.response().end("Password reset successful"))
                .onFailure(err -> ctx.response().setStatusCode(400).end(err.getMessage()));
    }

    public AuthHandler(Vertx vertx) {
        this.authService = new AuthService(vertx);
    }

    public void registerRoutes(Router router) {
        router.post("/api/register").handler(this::handleRegister);
        router.post("/api/login").handler(this::handleLogin);
        router.post("/api/logout").handler(this::handleLogout);
        router.post("/api/reset-password").handler(this::handleCompletePasswordReset);
        router.post("/api/refresh-token").handler(this::handleRefreshToken);
        router.post("/api/initiate-password-reset").handler(this::handleInitiatePasswordReset); // ✅ ADD THIS
    }




    private void handleRegister(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        String email = body.getString("email");

        if (email == null) {
            ctx.response().setStatusCode(400).end("Missing email");
            return;
        }

        authService.registerUser(email)
                .onSuccess(v -> ctx.response().setStatusCode(201).end("User registered, check your email"))
                .onFailure(err -> ctx.response().setStatusCode(400).end(err.getMessage()));
    }

    private void handleLogin(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        String email = body.getString("email");
        String password = body.getString("password");

        if (email == null || password == null) {
            ctx.response().setStatusCode(400).end("Missing email or password");
            return;
        }

        authService.authenticateUser(email, password)
                .onSuccess(token -> {
                    JsonObject res = new JsonObject().put("token", token);
                    ctx.response()
                            .putHeader("Content-Type", "application/json")
                            .end(res.encode());
                })
                .onFailure(err -> ctx.response().setStatusCode(401).end(err.getMessage()));
    }

    private void handleLogout(RoutingContext ctx) {
        String authHeader = ctx.request().getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            ctx.response().setStatusCode(400).end("Missing or invalid Authorization header");
            return;
        }

        String token = authHeader.substring("Bearer ".length());

        authService.logout(token)
                .onSuccess(v -> ctx.response().end("Logged out successfully"))
                .onFailure(err -> ctx.response().setStatusCode(500).end("Logout failed: " + err.getMessage()));
    }
    private void handleInitiatePasswordReset(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        String email = body.getString("email");

        if (email == null) {
            ctx.response().setStatusCode(400).end("Missing email");
            return;
        }

        authService.initiatePasswordReset(email)
                .onSuccess(v -> ctx.response().end("Password reset link sent to email"))
                .onFailure(err -> ctx.response().setStatusCode(400).end(err.getMessage()));
    }
    private void handleRefreshToken(RoutingContext ctx) {
        String authHeader = ctx.request().getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            ctx.response().setStatusCode(400).end("Missing or invalid Authorization header");
            return;
        }

        String oldToken = authHeader.substring("Bearer ".length());

        authService.refreshToken(oldToken)
                .onSuccess(newToken -> {
                    JsonObject res = new JsonObject().put("token", newToken);
                    ctx.response()
                            .putHeader("Content-Type", "application/json")
                            .end(res.encode());
                })
                .onFailure(err -> {
                    ctx.response().setStatusCode(401).end("Token refresh failed: " + err.getMessage());
                });
    }

}
