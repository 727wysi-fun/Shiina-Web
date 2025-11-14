package dev.osunolimits.routes.ap.badges;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.osunolimits.main.App;
import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.utils.osu.PermissionHelper;
import spark.Request;
import spark.Response;

public class ManageBadges extends Shiina {
    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);

        if (!shiina.loggedIn) {
            res.redirect("/login");
            return notFound(res, shiina);
        }

        // Проверяем наличие прав DEV
        if (!PermissionHelper.hasPrivileges(shiina.user.priv, PermissionHelper.Privileges.DEVELOPER)) {
            res.redirect("/");
            return notFound(res, shiina);
        }

        // Загружаем список всех бейджей
        List<Map<String, Object>> badges = new ArrayList<>();
        try {
            ResultSet rs = shiina.mysql.Query("SELECT b.id, b.name, b.description, b.image_url, COUNT(ub.user_id) as owner_count FROM badges b LEFT JOIN user_badges ub ON b.id = ub.badge_id GROUP BY b.id ORDER BY b.id DESC");
            if (rs != null) {
                while (rs.next()) {
                    Map<String, Object> badge = new HashMap<>();
                    badge.put("id", rs.getInt("id"));
                    badge.put("name", rs.getString("name"));
                    badge.put("description", rs.getString("description"));
                    
                    // Transform old database URLs to current format
                    String imageUrl = rs.getString("image_url");
                    if (imageUrl != null && imageUrl.contains("/")) {
                        // Extract filename from full URL (e.g., from "https://assets.727wysi.fun/badges/abc123.png" -> "abc123.png")
                        imageUrl = imageUrl.substring(imageUrl.lastIndexOf("/") + 1);
                        // Rebuild with current configuration
                        String badgeUrl = App.env.get("BADGEURL");
                        if (badgeUrl == null) badgeUrl = "https://727wysi.fun/badges/";
                        imageUrl = badgeUrl + imageUrl;
                    }
                    badge.put("imageUrl", imageUrl);
                    badge.put("ownerCount", rs.getInt("owner_count"));
                    badges.add(badge);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        shiina.data.put("badges", badges);
        shiina.data.put("actNav", 30); // Уникальный ID для активного пункта меню
        return renderTemplate("ap/badges/manage.html", shiina, res, req);
    }
}
