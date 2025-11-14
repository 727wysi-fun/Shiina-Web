package dev.osunolimits.routes.api.get;

import java.sql.ResultSet;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.utils.osu.PermissionHelper;
import spark.Request;
import spark.Response;

public class SearchUsers extends Shiina {
    private final Gson GSON = new Gson();

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);

        if (shiina.user == null || !shiina.loggedIn) {
            res.status(401);
            JsonObject response = new JsonObject();
            response.addProperty("error", "Unauthorized");
            return GSON.toJson(response);
        }

        // Только девелопер может искать пользователей для управления бейджами
        if (!PermissionHelper.hasPrivileges(shiina.user.priv, PermissionHelper.Privileges.DEVELOPER)) {
            res.status(403);
            JsonObject response = new JsonObject();
            response.addProperty("error", "Forbidden - Developer privileges required");
            return GSON.toJson(response);
        }

        String searchTerm = req.queryParams("q");
        if (searchTerm == null || searchTerm.trim().isEmpty() || searchTerm.length() < 2) {
            res.status(400);
            JsonObject response = new JsonObject();
            response.addProperty("error", "Search term must be at least 2 characters");
            return GSON.toJson(response);
        }

        try {
            // Ищем пользователей по имени (максимум 10 результатов)
            String query = "SELECT id, name as username, country FROM users WHERE safe_name LIKE ? LIMIT 10";
            ResultSet rs = shiina.mysql.Query(query, "%" + searchTerm.toLowerCase().replace(' ', '_') + "%");

            JsonArray users = new JsonArray();
            if (rs != null) {
                while (rs.next()) {
                    JsonObject user = new JsonObject();
                    user.addProperty("id", rs.getInt("id"));
                    user.addProperty("username", rs.getString("username"));
                    user.addProperty("country", rs.getString("country"));
                    users.add(user);
                }
            }

            shiina.mysql.close();
            res.type("application/json");
            res.status(200);
            return GSON.toJson(users);
        } catch (Exception e) {
            res.status(500);
            JsonObject response = new JsonObject();
            response.addProperty("error", "Internal server error");
            shiina.mysql.close();
            return GSON.toJson(response);
        }
    }
}
