package dev.osunolimits.routes.get;

import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import spark.Request;
import spark.Response;
import java.sql.ResultSet;

public class Shop extends Shiina {

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);
        shiina.data.put("actNav", -1); // no active nav
        
        // Check if user is authenticated
        if (shiina.user == null) {
            return redirect(res, shiina, "/login");
        }
        
        // Get user balance from database
        int userId = shiina.user.id;
        ResultSet userRs = shiina.mysql.Query("SELECT COALESCE(balance, 0) as balance FROM users WHERE id = ?", userId);
        
        long balance = 0;
        if (userRs.next()) {
            balance = userRs.getLong("balance");
        }
        
        shiina.data.put("balance", balance);
        
        return renderTemplate("shop.html", shiina, res, req);
    }
}
