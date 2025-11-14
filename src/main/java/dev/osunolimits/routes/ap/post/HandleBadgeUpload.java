package dev.osunolimits.routes.ap.post;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;

import javax.servlet.MultipartConfigElement;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import dev.osunolimits.main.App;
import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.utils.osu.PermissionHelper;
import spark.Request;
import spark.Response;

public class HandleBadgeUpload extends Shiina {
    private final Gson GSON = new Gson();
    private MultipartConfigElement multipartConfig;
    private static final int MAX_REQUEST_SIZE = 2 * 1024 * 1024; // 2MB

    public HandleBadgeUpload() {
        multipartConfig = new MultipartConfigElement(".temp/", MAX_REQUEST_SIZE, MAX_REQUEST_SIZE, 1);
    }

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);
        
        // Устанавливаем multipart конфигурацию
        req.raw().setAttribute("org.eclipse.jetty.multipartConfig", multipartConfig);

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

        try {
            String badgeName = null;
            String badgeDescription = null;
            
            // Получаем данные из multipart form-data
            try {
                javax.servlet.http.Part namePart = req.raw().getPart("name");
                if (namePart != null) {
                    byte[] nameBytes = namePart.getInputStream().readAllBytes();
                    if (nameBytes != null && nameBytes.length > 0) {
                        badgeName = new String(nameBytes).trim();
                    }
                }
            } catch (Exception e) {
                System.err.println("Error reading name part: " + e.getMessage());
            }
            
            try {
                javax.servlet.http.Part descPart = req.raw().getPart("description");
                if (descPart != null) {
                    byte[] descBytes = descPart.getInputStream().readAllBytes();
                    if (descBytes != null && descBytes.length > 0) {
                        badgeDescription = new String(descBytes).trim();
                    }
                }
            } catch (Exception e) {
                System.err.println("Error reading description part: " + e.getMessage());
            }
            
            // Логирование для отладки
            System.out.println("Received name: '" + badgeName + "'");
            System.out.println("Received description: '" + badgeDescription + "'");
            
            if (badgeName == null || badgeName.isEmpty() || 
                badgeDescription == null || badgeDescription.isEmpty()) {
                res.status(400);
                JsonObject response = new JsonObject();
                response.addProperty("error", "Name and description are required");
                response.addProperty("name_received", badgeName != null ? badgeName : "null");
                response.addProperty("desc_received", badgeDescription != null ? badgeDescription : "null");
                return GSON.toJson(response);
            }

            // Получаем загруженный файл
            javax.servlet.http.Part filePart = null;
            try {
                filePart = req.raw().getPart("image");
            } catch (Exception e) {
                System.err.println("Error reading image part: " + e.getMessage());
            }
            
            if (filePart == null) {
                res.status(400);
                JsonObject response = new JsonObject();
                response.addProperty("error", "Image file is required");
                return GSON.toJson(response);
            }
            
            String filename = filePart.getSubmittedFileName();
            if (filename == null || filename.trim().isEmpty()) {
                res.status(400);
                JsonObject response = new JsonObject();
                response.addProperty("error", "Image file is required");
                return GSON.toJson(response);
            }

            // Получаем папку для хранения бейджей
            String badgeFolder = System.getenv("BADGEDFOLDER");
            if (badgeFolder == null || badgeFolder.trim().isEmpty()) {
                badgeFolder = "/home/bancho.py-ex/.data/badges/";
            }

            // Создаём папку, если её нет
            File badgeFolderFile = new File(badgeFolder);
            if (!badgeFolderFile.exists()) {
                badgeFolderFile.mkdirs();
            }

            // Генерируем уникальное имя файла
            String fileExtension = filename.substring(filename.lastIndexOf(".")).toLowerCase();
            String allowedExtensions = ".png.jpg.jpeg.gif.webp";
            
            if (!allowedExtensions.contains(fileExtension)) {
                res.status(400);
                JsonObject response = new JsonObject();
                response.addProperty("error", "Only .png, .jpg, .jpeg, .gif, .webp files are allowed");
                return GSON.toJson(response);
            }

            String newFileName = UUID.randomUUID().toString() + fileExtension;
            String filePath = badgeFolder + newFileName;

            // Загружаем файл
            try (InputStream inputStream = filePart.getInputStream()) {
                Files.copy(inputStream, Paths.get(filePath));
            }

            // Определяем URL на основе конфигурации
            String badgeUrl = App.env.get("BADGEURL");
            if (badgeUrl == null || badgeUrl.trim().isEmpty()) {
                badgeUrl = "https://727wysi.fun/badges/";
            }
            
            String imageUrl = badgeUrl + newFileName;

            // Сохраняем бейдж в БД
            String insertQuery = "INSERT INTO badges (name, description, image_url) VALUES (?, ?, ?)";
            int badgeId = shiina.mysql.Exec(insertQuery, badgeName, badgeDescription, imageUrl);

            shiina.mysql.close();
            res.status(200);
            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("badge_id", badgeId);
            response.addProperty("image_url", imageUrl);
            return GSON.toJson(response);

        } catch (Exception e) {
            e.printStackTrace();
            res.status(500);
            JsonObject response = new JsonObject();
            response.addProperty("error", "Internal server error: " + e.getMessage());
            return GSON.toJson(response);
        }
    }
}
