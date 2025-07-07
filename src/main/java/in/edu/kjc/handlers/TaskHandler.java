package in.edu.kjc.handlers;

import in.edu.kjc.services.AuthService;
import in.edu.kjc.services.TaskService;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.JWTAuthHandler;

public class TaskHandler {

    private final TaskService taskService;
    private final AuthService authService;

    public TaskHandler(Vertx vertx) {
        this.taskService = new TaskService(vertx);
        this.authService = new AuthService(vertx);
    }

    public void registerRoutes(Router router) {
        JWTAuthHandler jwtHandler = JWTAuthHandler.create(authService.getJwtProvider());

        router.route("/api/tasks*").handler(jwtHandler);

        router.post("/api/tasks").handler(this::createTask);
        router.get("/api/tasks").handler(this::getTasksWithFilters);
        router.put("/api/tasks/:id/done").handler(this::toggleTaskCompletion);
        router.put("/api/tasks/:id").handler(this::editTask);
        router.delete("/api/tasks/:id").handler(this::deleteTask);
    }

    private void createTask(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        String title = body.getString("title");
        String description = body.getString("description");
        String reminderTime = body.getString("reminderTime");

        if (title == null || description == null) {
            ctx.response().setStatusCode(400).end("Missing title or description");
            return;
        }

        User user = ctx.user();
        String userEmail = user.principal().getString("email");

        taskService.createTask(userEmail, title, description, reminderTime)
                .onSuccess(v -> ctx.response().setStatusCode(201).end("Task created"))
                .onFailure(err -> ctx.response().setStatusCode(500).end(err.getMessage()));
    }

    private void getTasksWithFilters(RoutingContext ctx) {
        User user = ctx.user();
        String email = user.principal().getString("email");

        int page = parseQueryParam(ctx, "page", 1);
        int size = parseQueryParam(ctx, "size", 10);
        String status = ctx.request().getParam("status");
        String sortBy = ctx.request().getParam("sortBy");
        String sortOrder = ctx.request().getParam("sortOrder");

        taskService.getTasksWithFilters(email, page, size, status, sortBy, sortOrder)
                .onSuccess(tasks -> {
                    JsonObject response = new JsonObject().put("tasks", new JsonArray(tasks));
                    ctx.response().putHeader("Content-Type", "application/json").end(response.encode());
                })
                .onFailure(err -> ctx.response().setStatusCode(500).end(err.getMessage()));
    }

    private void editTask(RoutingContext ctx) {
        String taskId = ctx.pathParam("id");
        JsonObject updates = ctx.body().asJsonObject();

        taskService.editTask(taskId, updates)
                .onSuccess(v -> ctx.response().end("Task updated successfully"))
                .onFailure(err -> ctx.response().setStatusCode(500).end(err.getMessage()));
    }

    private void toggleTaskCompletion(RoutingContext ctx) {
        String taskId = ctx.pathParam("id");

        taskService.toggleTaskCompletion(taskId)
                .onSuccess(v -> ctx.response().end("Task status toggled"))
                .onFailure(err -> ctx.response().setStatusCode(500).end(err.getMessage()));
    }

    private void deleteTask(RoutingContext ctx) {
        String taskId = ctx.pathParam("id");

        taskService.deleteTask(taskId)
                .onSuccess(v -> ctx.response().end("Task deleted"))
                .onFailure(err -> ctx.response().setStatusCode(500).end(err.getMessage()));
    }

    private int parseQueryParam(RoutingContext ctx, String key, int defaultValue) {
        String param = ctx.request().getParam(key);
        try {
            return param != null ? Integer.parseInt(param) : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
