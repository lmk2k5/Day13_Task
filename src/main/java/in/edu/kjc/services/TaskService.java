package in.edu.kjc.services;

import in.edu.kjc.configs.MongoConfig;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.FindOptions;
import io.vertx.ext.mongo.MongoClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class TaskService {

    private final MongoClient mongo;
    private final Vertx vertx;
    private final MailService mailService;

    public TaskService(Vertx vertx) {
        this.vertx = vertx;
        this.mongo = MongoConfig.getClient(vertx);
        this.mailService = new MailService(vertx);
    }

    public Future<Void> createTask(String userEmail, String title, String description, String reminderTime) {
        JsonObject task = new JsonObject()
                .put("title", title)
                .put("description", description)
                .put("dueDate", (String) null)
                .put("priority", "medium")
                .put("isCompleted", false)
                .put("reminderTime", reminderTime)
                .put("createdAt", Instant.now().toString())
                .put("updatedAt", Instant.now().toString())
                .put("userEmail", userEmail);

        return mongo.insert("tasks", task).compose(id -> {
            if (reminderTime != null && !reminderTime.isEmpty()) {
                try {
                    Instant target = Instant.parse(reminderTime);
                    long delay = Duration.between(Instant.now(), target).toMillis();
                    if (delay > 0) {
                        vertx.setTimer(delay, timerId -> {
                            mailService.sendEmail(
                                    userEmail,
                                    "Task Reminder: " + title,
                                    "Hey! This is your reminder for:\n\nTitle: " + title +
                                            "\nDescription: " + description +
                                            "\nTime: " + reminderTime
                            );
                        });
                    }
                } catch (Exception e) {
                    System.err.println("Invalid reminderTime format: " + e.getMessage());
                }
            }
            return Future.succeededFuture();
        });
    }

    public Future<List<JsonObject>> getTasksForUser(String userEmail) {
        JsonObject query = new JsonObject().put("userEmail", userEmail);
        return mongo.find("tasks", query);
    }

    public Future<Void> markTaskAsDone(String taskId) {
        JsonObject query = new JsonObject().put("_id", taskId);
        JsonObject update = new JsonObject()
                .put("$set", new JsonObject()
                        .put("isCompleted", true)
                        .put("updatedAt", Instant.now().toString()));
        return mongo.updateCollection("tasks", query, update).mapEmpty();
    }

    public Future<Void> markTaskAsPending(String taskId) {
        JsonObject query = new JsonObject().put("_id", taskId);
        JsonObject update = new JsonObject()
                .put("$set", new JsonObject()
                        .put("isCompleted", false)
                        .put("updatedAt", Instant.now().toString()));
        return mongo.updateCollection("tasks", query, update).mapEmpty();
    }

    public Future<Void> deleteTask(String taskId) {
        JsonObject query = new JsonObject().put("_id", taskId);
        return mongo.removeDocument("tasks", query).mapEmpty();
    }

    public Future<Void> editTask(String taskId, JsonObject updates) {
        JsonObject query = new JsonObject().put("_id", taskId);

        JsonObject allowedFields = new JsonObject();
        if (updates.containsKey("title")) allowedFields.put("title", updates.getString("title"));
        if (updates.containsKey("description")) allowedFields.put("description", updates.getString("description"));
        if (updates.containsKey("dueDate")) allowedFields.put("dueDate", updates.getString("dueDate"));
        if (updates.containsKey("priority")) allowedFields.put("priority", updates.getString("priority"));
        if (updates.containsKey("reminderTime")) allowedFields.put("reminderTime", updates.getString("reminderTime"));

        allowedFields.put("updatedAt", Instant.now().toString());

        JsonObject update = new JsonObject().put("$set", allowedFields);

        return mongo.updateCollection("tasks", query, update).mapEmpty();
    }

    public Future<Void> toggleTaskCompletion(String taskId) {
        JsonObject query = new JsonObject().put("_id", taskId);

        return mongo.findOne("tasks", query, null).compose(task -> {
            if (task == null) {
                return Future.failedFuture("Task not found");
            }

            boolean currentStatus = task.getBoolean("isCompleted", false);
            boolean newStatus = !currentStatus;

            JsonObject update = new JsonObject().put("$set", new JsonObject()
                    .put("isCompleted", newStatus)
                    .put("updatedAt", Instant.now().toString()));

            return mongo.updateCollection("tasks", query, update).mapEmpty();
        });
    }

    public Future<List<JsonObject>> getTasksWithFilters(String userEmail, int page, int size, String status, String sortBy, String sortOrder) {
        JsonObject query = new JsonObject().put("userEmail", userEmail);
        if (status != null && !status.isEmpty()) {
            query.put("status", status);
        }

        JsonObject sort = new JsonObject();
        if (sortBy != null && !sortBy.isEmpty()) {
            sort.put(sortBy, "desc".equalsIgnoreCase(sortOrder) ? -1 : 1);
        }

        FindOptions options = new FindOptions()
                .setLimit(size)
                .setSkip((page - 1) * size)
                .setSort(sort);

        return mongo.findWithOptions("tasks", query, options);
    }
}
