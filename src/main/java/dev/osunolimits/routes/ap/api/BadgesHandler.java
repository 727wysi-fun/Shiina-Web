package dev.osunolimits.routes.ap.api;

import java.io.File;
import java.sql.ResultSet;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.utils.osu.PermissionHelper;
import spark.Request;
import spark.Response;

public class BadgesHandler extends Shiina {
    private final Gson GSON = new Gson();

    public enum BadgeAction {
        ASSIGN,
        REMOVE,
        DELETE,
        GET_OWNERS,
        NONE
    }

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);

        if (shiina.user == null || !shiina.loggedIn) {
            res.status(401);
            JsonObject response = new JsonObject();
            response.addProperty("error", "Unauthorized");
            return GSON.toJson(response);
        }

        // Только девелопер может управлять бейджами
        if (!PermissionHelper.hasPrivileges(shiina.user.priv, PermissionHelper.Privileges.DEVELOPER)) {
            res.status(403);
            JsonObject response = new JsonObject();
            response.addProperty("error", "Forbidden - Developer privileges required");
            return GSON.toJson(response);
        }

        BadgeAction action = BadgeAction.NONE;
        if (req.queryParams("action") != null) {
            try {
                action = BadgeAction.valueOf(req.queryParams("action").toUpperCase());
            } catch (Exception e) {
                action = BadgeAction.NONE;
            }
        }

        if (action == BadgeAction.NONE) {
            res.status(400);
            JsonObject response = new JsonObject();
            response.addProperty("error", "Invalid action");
            return GSON.toJson(response);
        }

        switch (action) {
            case ASSIGN: {
                return assignBadge(shiina, req, res);
            }
            case REMOVE: {
                return removeBadge(shiina, req, res);
            }
            case DELETE: {
                return deleteBadge(shiina, req, res);
            }
            case GET_OWNERS: {
                return getBadgeOwners(shiina, req, res);
            }
            default:
                res.status(400);
                JsonObject response = new JsonObject();
                response.addProperty("error", "Invalid action");
                return GSON.toJson(response);
        }
    }

    private Object getBadgeOwners(ShiinaRequest shiina, Request req, Response res) throws Exception {
        String badgeIdStr = req.queryParams("badge_id");

        if (badgeIdStr == null) {
            res.status(400);
            JsonObject response = new JsonObject();
            response.addProperty("error", "Missing badge_id");
            return GSON.toJson(response);
        }

        try {
            int badgeId = Integer.parseInt(badgeIdStr);

            com.google.gson.JsonArray owners = new com.google.gson.JsonArray();

            try {
                ResultSet rs = shiina.mysql.Query("SELECT u.id, u.name, u.country FROM users u LEFT JOIN user_badges ub ON u.id = ub.user_id WHERE ub.badge_id = " + badgeId + " ORDER BY u.name ASC");
                if (rs != null) {
                    while (rs.next()) {
                        JsonObject owner = new JsonObject();
                        owner.addProperty("id", rs.getInt("id"));
                        owner.addProperty("username", rs.getString("name"));
                        String country = rs.getString("country");
                        if (country != null && !country.isEmpty()) {
                            owner.addProperty("country", country);
                        } else {
                            owner.addProperty("country", "xx");
                        }
                        owners.add(owner);
                    }
                }
            } catch (Exception e) {
                System.err.println("Error querying badge owners: " + e.getMessage());
                e.printStackTrace();
            }

            JsonObject response = new JsonObject();
            response.add("owners", owners);
            return GSON.toJson(response);
        } catch (NumberFormatException e) {
            res.status(400);
            JsonObject response = new JsonObject();
            response.addProperty("error", "Invalid badge_id format");
            return GSON.toJson(response);
        }
    }

    private Object assignBadge(ShiinaRequest shiina, Request req, Response res) throws Exception {
        String badgeIdStr = req.queryParams("badge_id");
        String userIdStr = req.queryParams("user_id");

        if (badgeIdStr == null || userIdStr == null) {
            res.status(400);
            JsonObject response = new JsonObject();
            response.addProperty("error", "Missing badge_id or user_id");
            return GSON.toJson(response);
        }

        try {
            int badgeId = Integer.parseInt(badgeIdStr);
            int userId = Integer.parseInt(userIdStr);
            
            // Проверяем существование бейджа
            ResultSet badgeCheck = shiina.mysql.Query("SELECT id FROM badges WHERE id = ?", badgeId);
            if (badgeCheck == null || !badgeCheck.next()) {
                res.status(404);
                JsonObject response = new JsonObject();
                response.addProperty("error", "Badge not found");
                return GSON.toJson(response);
            }

            // Проверяем существование пользователя
            ResultSet userCheck = shiina.mysql.Query("SELECT id FROM users WHERE id = ?", userId);
            if (userCheck == null || !userCheck.next()) {
                res.status(404);
                JsonObject response = new JsonObject();
                response.addProperty("error", "User not found");
                return GSON.toJson(response);
            }

            // Назначаем бейдж пользователю
            shiina.mysql.Exec("INSERT INTO user_badges (user_id, badge_id) VALUES (?, ?) ON DUPLICATE KEY UPDATE user_id=user_id", userId, badgeId);

            res.status(200);
            JsonObject response = new JsonObject();
            response.addProperty("success", "Badge assigned successfully");
            return GSON.toJson(response);
        } catch (NumberFormatException e) {
            res.status(400);
            JsonObject response = new JsonObject();
            response.addProperty("error", "Invalid badge_id or user_id format");
            return GSON.toJson(response);
        }
    }

    private Object removeBadge(ShiinaRequest shiina, Request req, Response res) throws Exception {
        String badgeIdStr = req.queryParams("badge_id");
        String userIdStr = req.queryParams("user_id");

        if (badgeIdStr == null || userIdStr == null) {
            res.status(400);
            JsonObject response = new JsonObject();
            response.addProperty("error", "Missing badge_id or user_id");
            return GSON.toJson(response);
        }

        try {
            int badgeId = Integer.parseInt(badgeIdStr);
            int userId = Integer.parseInt(userIdStr);

            // Удаляем бейдж у пользователя
            shiina.mysql.Exec("DELETE FROM user_badges WHERE user_id = ? AND badge_id = ?", userId, badgeId);

            res.status(200);
            JsonObject response = new JsonObject();
            response.addProperty("success", "Badge removed successfully");
            return GSON.toJson(response);
        } catch (NumberFormatException e) {
            res.status(400);
            JsonObject response = new JsonObject();
            response.addProperty("error", "Invalid badge_id or user_id format");
            return GSON.toJson(response);
        }
    }

    private Object deleteBadge(ShiinaRequest shiina, Request req, Response res) throws Exception {
        String badgeIdStr = req.queryParams("badge_id");

        if (badgeIdStr == null) {
            res.status(400);
            JsonObject response = new JsonObject();
            response.addProperty("error", "Missing badge_id");
            return GSON.toJson(response);
        }

        try {
            int badgeId = Integer.parseInt(badgeIdStr);

            // Получаем информацию о бейдже перед удалением
            ResultSet badgeInfo = shiina.mysql.Query("SELECT image_url FROM badges WHERE id = ?", badgeId);
            String imageUrl = null;
            if (badgeInfo != null && badgeInfo.next()) {
                imageUrl = badgeInfo.getString("image_url");
            }

            // Удаляем бейдж из БД (каскадное удаление удалит и связи с пользователями)
            shiina.mysql.Exec("DELETE FROM badges WHERE id = ?", badgeId);

            // Удаляем файл с диска если URL существует
            if (imageUrl != null && !imageUrl.isEmpty()) {
                try {
                    // Извлекаем имя файла из URL
                    String filename = imageUrl.substring(imageUrl.lastIndexOf("/") + 1);
                    String badgeFolder = System.getenv("BADGEDFOLDER");
                    if (badgeFolder == null) {
                        badgeFolder = "/home/bancho.py-ex/.data/badges/";
                    }
                    File badgeFile = new File(badgeFolder + filename);
                    if (badgeFile.exists()) {
                        badgeFile.delete();
                    }
                } catch (Exception e) {
                    // Логируем ошибку но не прерываем удаление из БД
                    System.err.println("Failed to delete badge file: " + e.getMessage());
                }
            }

            res.status(200);
            JsonObject response = new JsonObject();
            response.addProperty("success", "Badge deleted successfully");
            return GSON.toJson(response);
        } catch (NumberFormatException e) {
            res.status(400);
            JsonObject response = new JsonObject();
            response.addProperty("error", "Invalid badge_id format");
            return GSON.toJson(response);
        }
    }
}
