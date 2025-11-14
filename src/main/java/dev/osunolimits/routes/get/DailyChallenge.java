package dev.osunolimits.routes.get;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import dev.osunolimits.main.App;
import dev.osunolimits.models.FullBeatmap;
import dev.osunolimits.models.UserInfoObject;
import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.modules.utils.SEOBuilder;
import dev.osunolimits.utils.osu.OsuConverter;
import spark.Request;
import spark.Response;

public class DailyChallenge extends Shiina {
    public static int pageSize = 25;

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);
        shiina.data.put("actNav", 6); // Daily Challenge nav item
        
        App.log.info("DailyChallenge: Starting to handle request");

        // Получаем текущий активный daily challenge
        ResultSet challengeRs = shiina.mysql.Query("""
            SELECT dc.*, m.*, m.id as map_id
            FROM daily_challenges dc
            JOIN maps m ON dc.map_md5 = m.md5
            WHERE dc.active = 1 
            AND dc.start_time <= NOW() 
            AND dc.end_time > NOW()
            ORDER BY dc.start_time DESC 
            LIMIT 1
        """);

        if (!challengeRs.next()) {
            App.log.info("DailyChallenge: No active challenge found");
            // Если нет активного челленджа, показываем сообщение
            shiina.data.put("noActiveChallenge", true);
            return renderTemplate("daily.html", shiina, res, req);
        }
        App.log.info("DailyChallenge: Found active challenge");

        // Получаем информацию о карте
        FullBeatmap beatmap = new FullBeatmap();
        beatmap.setSetId(challengeRs.getInt("set_id"));
        beatmap.setMd5(challengeRs.getString("md5"));
        beatmap.setTitle(challengeRs.getString("title"));
        beatmap.setArtist(challengeRs.getString("artist"));
        beatmap.setVersion(challengeRs.getString("version"));
        beatmap.setCreator(challengeRs.getString("creator"));
        beatmap.setBpm(challengeRs.getDouble("bpm"));
        beatmap.setCs(challengeRs.getDouble("cs"));
        beatmap.setAr(challengeRs.getDouble("ar"));
        beatmap.setOd(challengeRs.getDouble("od"));
        beatmap.setHp(challengeRs.getDouble("hp"));
        beatmap.setDiff(challengeRs.getDouble("diff"));
        beatmap.setMode(challengeRs.getInt("mode"));

        // Получаем топ скоры для этого челленджа
        List<FullBeatmap.BeatmapScore> scores = new ArrayList<>();
        ResultSet scoresRs = shiina.mysql.Query("""
            SELECT s.id, s.pp, s.score, s.grade, s.play_time, s.userid, s.mods,
                   u.name, u.country, u.priv
            FROM scores s
            LEFT JOIN users u ON s.userid = u.id
            WHERE s.daily_challenge_id = ?
            AND s.status = 2
            ORDER BY s.pp DESC, s.play_time ASC
            LIMIT ?
        """, challengeRs.getInt("id"), pageSize);

        while (scoresRs.next()) {
            FullBeatmap.BeatmapScore score = beatmap.new BeatmapScore();
            score.setId(scoresRs.getInt("id"));
            score.setPp(scoresRs.getInt("pp"));
            score.setScore(scoresRs.getLong("score"));
            score.setGrade(scoresRs.getString("grade"));
            score.setPlayTime(scoresRs.getString("play_time"));
            score.setUserId(scoresRs.getInt("userid"));
            score.setMods(OsuConverter.convertMods(scoresRs.getInt("mods")));
            
            UserInfoObject user = new UserInfoObject();
            user.name = scoresRs.getString("name");
            user.country = scoresRs.getString("country");
            score.setUser(user);
            score.setUser(user);
            
            scores.add(score);
        }

        beatmap.setScores(scores.toArray(new FullBeatmap.BeatmapScore[0]));

        App.log.info("DailyChallenge: Found {} scores for challenge", scores.size());

        // Добавляем данные для шаблона
        String peppyImageUrl = "https://assets.ppy.sh/beatmaps/" + challengeRs.getInt("set_id") + "/covers/cover.jpg?1650681317";
        SEOBuilder seo = new SEOBuilder("Daily Challenge - " + beatmap.getTitle(), App.customization.get("homeDescription").toString(), peppyImageUrl);
        
        shiina.data.put("seo", seo);
        // Сохраняем нужные данные из ResultSet
        shiina.data.put("beatmap", beatmap);
        shiina.data.put("challengeId", challengeRs.getInt("id"));
        shiina.data.put("mapId", challengeRs.getInt("map_id")); // ID карты из таблицы maps через алиас
        
        // Convert end_time to ISO 8601 format with UTC timezone for consistency
        Timestamp endTimestamp = challengeRs.getTimestamp("end_time");
        ZonedDateTime zonedDateTime = endTimestamp.toInstant().atZone(ZoneId.of("UTC"));
        String challengeEndTimeISO = zonedDateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        shiina.data.put("challengeEndTime", challengeEndTimeISO);
        
        shiina.data.put("mode", challengeRs.getInt("mode"));
        shiina.data.put("avatarServer", App.env.get("AVATARSRV"));

        App.log.info("DailyChallenge: Rendering template with beatmap {}", beatmap.getTitle());
        return renderTemplate("daily.html", shiina, res, req);
    }
}