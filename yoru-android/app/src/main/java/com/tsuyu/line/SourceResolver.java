package com.tsuyu.line;

import android.net.Uri;
import org.json.*;
import java.io.IOException;
import java.util.*;
import java.util.regex.*;

public final class SourceResolver {
    private static String publicToken = "";
    private static long tokenAt;

    private SourceResolver() {}

    public static synchronized String token(ApiRepository repo) throws Exception {
        if (!publicToken.isEmpty() && System.currentTimeMillis() - tokenAt < 300000) return publicToken;
        String script = repo.request(Sec.s("3c07010906694a4c190a10220e5453545618371c18561437014e02091532000b411e5f5f3a5d1f0a4a255851"), "GET", null, false);
        String[] patterns = {
                "token\\s*=\\s*[\"\\\']([A-Za-z0-9]{16,120})[\"\\\']",
                "[\"\\\']token[\"\\\']\\s*:\\s*[\"\\\']([A-Za-z0-9]{16,120})[\"\\\']",
                "token=\\\\?[\"\\\']([A-Za-z0-9]{16,120})[\"\\\']"
        };
        for (String pattern : patterns) {
            Matcher m = Pattern.compile(pattern).matcher(script);
            if (m.find()) {
                publicToken = m.group(1);
                tokenAt = System.currentTimeMillis();
                return publicToken;
            }
        }
        throw new IOException("Просмотр временно недоступен");
    }

    public static String directKodik(int mal, ApiRepository repo) throws Exception {
        if (mal <= 0) throw new IOException("Не удалось найти аниме в этом каталоге");
        String q = repo.query(Sec.s("3c07010906-694a4c190a-10220e5453-405b18371c-1856123611-4e02091532-000b"), repo.params("title", "Player", "hasPlayer", "false", "url", Sec.s("3c07010906694a4c190a10220e1d501e5159395c13101b3748131e040d2e174641585b5d3d1e1a0b1c1a215e") + mal, "token", token(repo), "shikimoriID", String.valueOf(mal)));
        JSONObject j = repo.get(q);
        if (j.has("error") || !j.optBoolean("found")) throw new IOException("У этого каталога пока нет просмотра");
        if (j.has("allowed") && j.optInt("allowed", 1) == 0) throw new IOException("Просмотр временно ограничен");
        String u = embed(j.optString("link"));
        if (u.isEmpty()) throw new IOException("Просмотр временно недоступен");
        return u;
    }

    public static Anime kodikShell(Anime base) {
        Anime a = new Anime();
        a.source = "kodik";
        a.id = String.valueOf(base.malId > 0 ? base.malId : base.id.matches("\\d+") ? Integer.parseInt(base.id) : (int) (1L + (Integer.toUnsignedLong(base.key().hashCode()) % 999999999L)));
        a.title = base.title;
        a.original = base.original;
        a.alias = base.alias;
        a.poster = base.poster;
        a.description = base.description;
        a.year = base.year;
        a.type = base.type;
        a.status = base.status;
        a.age = base.age;
        a.episodes = base.episodes;
        a.malId = base.malId > 0 ? base.malId : CalendarApi.parseInt(base.id);
        a.anilistId = base.anilistId;
        a.kpId = base.kpId;
        a.libriaAlias = base.libriaAlias;
        a.score = base.score;
        a.trailerUrl = base.trailerUrl;
        a.airedDate = base.airedDate;
        a.screenshots.addAll(base.screenshots);
        a.genres.addAll(base.genres);
        return a;
    }

    public static Anime kodikDetails(Anime base, boolean episodes, ApiRepository repo) throws Exception {
        Anime shell = kodikShell(base);
        if (shell.description.isEmpty() || shell.poster.isEmpty()) {
            try {
                Anime sh = new Anime();
                sh.source = "shikimori";
                sh.id = String.valueOf(shell.malId > 0 ? shell.malId : CalendarApi.parseInt(shell.id));
                sh.title = shell.title;
                sh.original = shell.original;
                Anime full = repo.details(sh, false);
                shell = kodikShell(full);
            } catch (Exception ignored) {}
        }
        if (episodes) {
            int mal = shell.malId > 0 ? shell.malId : CalendarApi.parseInt(shell.id);
            String url = directKodik(mal, repo);
            int count = Math.max(1, Math.min(250, shell.episodes > 0 ? shell.episodes : 1));
            for (int i = 1; i <= count; i++) {
                Anime.Episode ep = new Anime.Episode();
                ep.id = shell.id + "-" + i;
                ep.number = i;
                ep.name = "";
                ep.lazy = "kodik";
                ep.resolverUrl = Uri.parse(url).buildUpon().appendQueryParameter("episode", String.valueOf(i)).build().toString();
                ep.variants.add(new Anime.Variant("", "Tsuyu", ep.resolverUrl));
                shell.episodeList.add(ep);
            }
        }
        return repo.remember(shell);
    }

    public static String embed(String input) {
        if (input == null) return "";
        String s = input.trim().replace("&amp;", "&");
        Matcher html = Pattern.compile("src=[\"']([^\"']+)", Pattern.CASE_INSENSITIVE).matcher(s);
        if (html.find()) s = html.group(1);
        if (s.startsWith("//")) s = "https:" + s;
        try {
            Uri u = Uri.parse(s);
            String host = u.getHost();
            if (host != null && Arrays.asList(Sec.s("351d1c081c-274b001d08"), Sec.s("3f1c11101e-7d0c0d140a"), Sec.s("3f1c11101e-7d0600"), Sec.s("3f1c11101e-7d070a08")).contains(host)) {
                s = u.buildUpon().scheme("https").authority("kodikplayer.com").build().toString();
            }
            return ApiRepository.safeUrl(s);
        } catch (Exception e) {
            return "";
        }
    }
}
