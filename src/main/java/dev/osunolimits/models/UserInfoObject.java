package dev.osunolimits.models;

import java.util.List;

import com.google.gson.Gson;

import dev.osunolimits.main.App;
import lombok.Data;

@Data
public class UserInfoObject {
    private final static Gson gson = new Gson();
    public UserInfoObject() {
    }   
    public UserInfoObject(int id) {
        String userJson = App.jedisPool.get("shiina:user:" + id);
        if (userJson == null) {
            // Set default values if user info not in Redis
            this.id = id;
            this.name = "Unknown";
            this.safe_name = "unknown";
            this.priv = 0;
            this.groups = List.of();
            return;
        }
        
        UserInfoObject userInfo = gson.fromJson(userJson, UserInfoObject.class);
        if (userInfo == null) {
            throw new IllegalStateException("Failed to parse user info from Redis for id: " + id);
        }
        
        this.id = userInfo.id;
        this.name = userInfo.name;
        this.safe_name = userInfo.safe_name;
        this.priv = userInfo.priv;
        this.groups = userInfo.groups;
        this.country = userInfo.country;
    }

    public int id;
    public String name;
    public String safe_name;
    public int priv;
    public List<Group> groups;
    public String country;
}
