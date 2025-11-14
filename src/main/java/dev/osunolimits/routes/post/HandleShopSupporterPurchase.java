package dev.osunolimits.routes.post;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.osunolimits.main.App;
import dev.osunolimits.models.UserInfoObject;
import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.routes.ap.api.PubSubModels;
import spark.Request;
import spark.Response;

import java.sql.ResultSet;

public class HandleShopSupporterPurchase extends Shiina {
    private final Gson GSON = new Gson();

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);

        if (shiina.user == null || !shiina.loggedIn) {
            res.status(401);
            JsonObject response = new JsonObject();
            response.addProperty("error", "Not authenticated");
            return GSON.toJson(response);
        }

        try {
            // Get parameters
            int days = Integer.parseInt(req.queryParams("days") != null ? req.queryParams("days") : "0");
            int cost = 0;

            // Validate days and set cost
            if (days == 30) {
                cost = 300;
            } else if (days == 60) {
                cost = 500;
            } else if (days == 90) {
                cost = 800;
            } else {
                res.status(400);
                JsonObject response = new JsonObject();
                response.addProperty("error", "Invalid duration selected");
                return GSON.toJson(response);
            }

            // Get current balance
            ResultSet balanceRs = shiina.mysql.Query("SELECT COALESCE(balance, 0) as balance FROM users WHERE id = ?", shiina.user.id);
            int currentBalance = 0;
            if (balanceRs.next()) {
                currentBalance = balanceRs.getInt("balance");
            }

            // Check if user has enough coins
            if (currentBalance < cost) {
                res.status(400);
                JsonObject response = new JsonObject();
                response.addProperty("error", "Insufficient coins. Required: " + cost + ", Available: " + currentBalance);
                return GSON.toJson(response);
            }

            // Deduct balance
            shiina.mysql.Exec("UPDATE users SET balance = balance - ? WHERE id = ?", cost, shiina.user.id);

            // Give donator via PubSub
            PubSubModels models = new PubSubModels();
            PubSubModels.GiveDonatorInput giveDonatorInput = models.new GiveDonatorInput();
            giveDonatorInput.setId(shiina.user.id);
            giveDonatorInput.setDuration(days + "d");

            Gson gsonLib = new Gson();
            App.jedisPool.publish("givedonator", gsonLib.toJson(giveDonatorInput));

            // Wait for the update to propagate
            Thread.sleep(500);

            // Update Redis cache
            UserInfoObject obj = gsonLib.fromJson(App.jedisPool.get("shiina:user:" + shiina.user.id), UserInfoObject.class);
            ResultSet privRs = shiina.mysql.Query("SELECT `priv` FROM `users` WHERE `id` = ?", shiina.user.id);
            if (obj != null) {
                obj.priv = privRs.next() ? privRs.getInt("priv") : 0;
                String userJson = gsonLib.toJson(obj);
                App.jedisPool.del("shiina:user:" + shiina.user.id);
                App.jedisPool.set("shiina:user:" + shiina.user.id, userJson);
            }

            // Get new balance
            ResultSet newBalanceRs = shiina.mysql.Query("SELECT COALESCE(balance, 0) as balance FROM users WHERE id = ?", shiina.user.id);
            int newBalance = 0;
            if (newBalanceRs.next()) {
                newBalance = newBalanceRs.getInt("balance");
            }

            res.status(200);
            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("message", "Supporter status purchased successfully!");
            response.addProperty("new_balance", newBalance);
            return GSON.toJson(response);

        } catch (NumberFormatException e) {
            res.status(400);
            JsonObject response = new JsonObject();
            response.addProperty("error", "Invalid parameters");
            return GSON.toJson(response);
        } catch (Exception e) {
            e.printStackTrace();
            res.status(500);
            JsonObject response = new JsonObject();
            response.addProperty("error", "An error occurred: " + e.getMessage());
            return GSON.toJson(response);
        } finally {
            shiina.mysql.close();
        }
    }
}
