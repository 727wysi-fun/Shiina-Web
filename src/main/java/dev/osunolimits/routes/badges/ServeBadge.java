package dev.osunolimits.routes.badges;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

import dev.osunolimits.modules.Shiina;
import spark.Request;
import spark.Response;

public class ServeBadge extends Shiina {
    @Override
    public Object handle(Request req, Response res) throws Exception {
        String badgeFileName = req.params(":filename");
        
        if (badgeFileName == null || badgeFileName.trim().isEmpty()) {
            res.status(404);
            return "Badge not found";
        }
        
        // Получаем папку для хранения бейджей
        String badgeFolder = System.getenv("BADGEDFOLDER");
        if (badgeFolder == null || badgeFolder.trim().isEmpty()) {
            badgeFolder = "/home/bancho.py-ex/.data/badges/";
        }
        
        // Проверяем, что имя файла не содержит опасных символов (предотвращение path traversal)
        if (badgeFileName.contains("..") || badgeFileName.contains("/") || badgeFileName.contains("\\")) {
            res.status(403);
            return "Access denied";
        }
        
        String filePath = badgeFolder + badgeFileName;
        File badgeFile = new File(filePath);
        
        // Проверяем, что файл существует и находится в правильной папке
        if (!badgeFile.exists() || !badgeFile.isFile()) {
            res.status(404);
            return "Badge not found";
        }
        
        // Проверяем, что файл находится в папке бейджей
        String canonicalPath = badgeFile.getCanonicalPath();
        String canonicalBadgeFolder = new File(badgeFolder).getCanonicalPath();
        if (!canonicalPath.startsWith(canonicalBadgeFolder)) {
            res.status(403);
            return "Access denied";
        }
        
        try {
            // Определяем тип контента
            String contentType = "application/octet-stream";
            if (badgeFileName.toLowerCase().endsWith(".png")) {
                contentType = "image/png";
            } else if (badgeFileName.toLowerCase().endsWith(".jpg") || badgeFileName.toLowerCase().endsWith(".jpeg")) {
                contentType = "image/jpeg";
            } else if (badgeFileName.toLowerCase().endsWith(".gif")) {
                contentType = "image/gif";
            } else if (badgeFileName.toLowerCase().endsWith(".webp")) {
                contentType = "image/webp";
            }
            
            res.type(contentType);
            
            // Читаем файл и возвращаем его содержимое
            return Files.readAllBytes(Paths.get(filePath));
        } catch (Exception e) {
            e.printStackTrace();
            res.status(500);
            return "Error reading badge file: " + e.getMessage();
        }
    }
}
