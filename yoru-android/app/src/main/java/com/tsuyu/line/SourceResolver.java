package com.tsuyu.line;

import android.net.Uri;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

public final class SourceResolver {
    private static String publicToken = "";
    private static long tokenAt;
    private static final long TOKEN_TTL = 4L * 60L * 60L * 1000L;

    private SourceResolver() {}

    private static String readString(File f) {
        try (FileInputStream fis = new FileInputStream(f);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[512];
            int n;
            while ((n = fis.read(buf)) != -1) bos.write(buf, 0, n);
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private static void writeString(File f, String s) {
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(s.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }

    public static synchronized void invalidateToken() {
        publicToken = "";
        tokenAt = 0;
        try {
            YoruApp app = YoruApp.app();
            if (app != null && app.getCacheDir() != null) {
                new File(app.getCacheDir(), "kodik_tok.dat").delete();
            }
        } catch (Exception ignored) {}
    }

    public static void prewarm(ApiRepository repo) {
        YoruApp app = YoruApp.app();
        if (app == null || app.discovery == null) return;
        app.discovery.execute(() -> {
            try {
                token(repo);
            } catch (Exception ignored) {}
        });
    }

    private static final String[] FALLBACK_TOKENS = {
            Sec.s("641545404d63505a4204412e074c055150026247451d106b01014451177f5d40"),
            Sec.s("3146414d436350521450442a564f565401576146141d4037015642554229551d"),
            Sec.s("6045174e146a505516514d73541801540b0f6040401b433056521003152f5241"),
            Sec.s("6d4a454d106652514a04477b0348060607536247114c4536075610554579001b")
    };

    public static synchronized String token(ApiRepository repo) throws Exception {
        if (!publicToken.isEmpty() && System.currentTimeMillis() - tokenAt < TOKEN_TTL) return publicToken;
        YoruApp app = YoruApp.app();
        if (app != null && app.getCacheDir() != null) {
            File f = new File(app.getCacheDir(), "kodik_tok.dat");
            if (f.exists() && System.currentTimeMillis() - f.lastModified() < TOKEN_TTL) {
                String saved = readString(f).trim();
                if (saved.length() >= 16 && saved.length() <= 120) {
                    publicToken = saved;
                    tokenAt = f.lastModified();
                    return publicToken;
                }
            }
        }
        try {
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
                    if (app != null && app.getCacheDir() != null) {
                        writeString(new File(app.getCacheDir(), "kodik_tok.dat"), publicToken);
                    }
                    return publicToken;
                }
            }
        } catch (Exception ignored) {}
        for (String fb : FALLBACK_TOKENS) {
            if (fb != null && !fb.isEmpty()) {
                publicToken = fb;
                tokenAt = System.currentTimeMillis();
                return publicToken;
            }
        }
        throw new IOException("Просмотр временно недоступен");
    }

    public static String directKodik(int mal, ApiRepository repo) throws Exception {
        if (mal <= 0) throw new IOException("Не удалось найти аниме в этом каталоге");
        String q = repo.query(Sec.s("3c07010906-694a4c190a-10220e5453-405b18371c-1856123611-4e02091532-000b"), repo.params("title", "Player", "hasPlayer", "false", "url", Sec.s("3c07010906694a4c190a10220e1d501e5159395c13101b3748131e040d2e174641585b5d3d1e1a0b1c1a215e") + mal, "token", token(repo), Sec.s("271b1c121c3e0a111b2c30"), String.valueOf(mal)));
        JSONObject j = repo.get(q);
        if (j.has("error") || !j.optBoolean("found")) {
            invalidateToken();
            String q2 = repo.query(Sec.s("3c07010906-694a4c190a-10220e5453-405b18371c-1856123611-4e02091532-000b"), repo.params("title", "Player", "hasPlayer", "false", "url", Sec.s("3c07010906694a4c190a10220e1d501e5159395c13101b3748131e040d2e174641585b5d3d1e1a0b1c1a215e") + mal, "token", token(repo), Sec.s("271b1c121c3e0a111b2c30"), String.valueOf(mal)));
            j = repo.get(q2);
            if (j.has("error") || !j.optBoolean("found")) throw new IOException("У этого каталога пока нет просмотра");
        }
        if (j.has("allowed") && j.optInt("allowed", 1) == 0) throw new IOException("Просмотр временно ограничен");
        String u = embed(j.optString("link"));
        if (u.isEmpty()) throw new IOException("Просмотр временно недоступен");
        return u;
    }

    public static Anime kodikShell(Anime base) {
        Anime a = new Anime();
        a.source = Sec.s("3f1c11101e");
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
        a.episodesAired = base.episodesAired;
        a.nextEpisodeAt = base.nextEpisodeAt;
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
        if (!episodes && (shell.description.isEmpty() || shell.poster.isEmpty())) {
            try {
                Anime sh = new Anime();
                sh.source = Sec.s("271b1c121c3e0a111b");
                sh.id = String.valueOf(shell.malId > 0 ? shell.malId : CalendarApi.parseInt(shell.id));
                sh.title = shell.title;
                sh.original = shell.original;
                Anime full = repo.details(sh, false);
                shell = kodikShell(full);
            } catch (Exception ignored) {}
        }
        if (episodes) {
            int mal = shell.malId > 0 ? shell.malId : CalendarApi.parseInt(shell.id);
            int total = Math.max(1, Math.min(250, shell.episodes > 0 ? shell.episodes : (shell.episodesAired > 0 ? shell.episodesAired : (base.episodes > 0 ? base.episodes : (base.episodesAired > 0 ? base.episodesAired : 12)))));
            int aired = shell.episodesAired > 0 ? shell.episodesAired : (base.episodesAired > 0 ? base.episodesAired : (shell.finished() ? total : 0));
            JSONArray results = null;
            try {
                String searchUrl = mal > 0
                        ? repo.query(Sec.s("3c07010906694a4c190a10220e1842591c553b1e5a0a103217001a"), repo.params("token", token(repo), Sec.s("271b1c121c3e0a111b3a1d2f"), String.valueOf(mal), Sec.s("231a01112a36150a010a102e16"), "true"))
                        : repo.query(Sec.s("3c07010906694a4c190a10220e1842591c553b1e5a0a103217001a"), repo.params("token", token(repo), "title", shell.title, Sec.s("231a01112a36150a010a102e16"), "true"));
                JSONObject root = repo.get(searchUrl);
                results = root.optJSONArray("results");
                if ((results == null || results.length() == 0) && mal > 0 && !shell.title.isEmpty()) {
                    String fallbackSearch = repo.query(Sec.s("3c07010906694a4c190a10220e1842591c553b1e5a0a103217001a"), repo.params("token", token(repo), "title", shell.title, Sec.s("231a01112a36150a010a102e16"), "true"));
                    JSONObject fbRoot = repo.get(fallbackSearch);
                    results = fbRoot.optJSONArray("results");
                }
                if ((results == null || results.length() == 0) && !shell.original.isEmpty()) {
                    String origSearch = repo.query(Sec.s("3c07010906694a4c190a10220e1842591c553b1e5a0a103217001a"), repo.params("token", token(repo), "title", shell.original, Sec.s("231a01112a36150a010a102e16"), "true"));
                    JSONObject origRoot = repo.get(origSearch);
                    results = origRoot.optJSONArray("results");
                }
            } catch (Exception ignored) {}
            String defaultUrl = "";
            try {
                defaultUrl = directKodik(mal, repo);
            } catch (Exception ignored) {}
            for (int i = 1; i <= total; i++) {
                Anime.Episode ep = new Anime.Episode();
                ep.id = shell.id + "-" + i;
                ep.number = i;
                boolean isFuture = !shell.finished() && aired > 0 && i > aired;
                ep.name = "";
                ep.lazy = Sec.s("3f1c11101e");
                if (results != null && results.length() > 0) {
                    HashSet<String> seen = new HashSet<>();
                    for (int r = 0; r < results.length(); r++) {
                        JSONObject item = results.optJSONObject(r);
                        if (item == null) continue;
                        JSONObject tr = item.optJSONObject("translation");
                        String voiceName = tr != null ? tr.optString("title", "") : "";
                        if (voiceName.isEmpty()) voiceName = item.optString("title", "");
                        if (voiceName.isEmpty()) voiceName = "Озвучка";
                        String epLink = "";
                        JSONObject seasons = item.optJSONObject("seasons");
                        if (seasons != null) {
                            Iterator<String> sIt = seasons.keys();
                            while (sIt.hasNext() && epLink.isEmpty()) {
                                JSONObject sObj = seasons.optJSONObject(sIt.next());
                                if (sObj != null) {
                                    JSONObject eps = sObj.optJSONObject("episodes");
                                    if (eps != null && eps.has(String.valueOf(i))) {
                                        epLink = eps.optString(String.valueOf(i), "");
                                    }
                                }
                            }
                        }
                        if (epLink.isEmpty() && (!isFuture || i == 1)) {
                            epLink = item.optString("link", "");
                            if (!epLink.isEmpty() && i > 1 && !epLink.contains("episode=")) {
                                epLink = Uri.parse(epLink).buildUpon().appendQueryParameter("episode", String.valueOf(i)).build().toString();
                            }
                        }
                        String safe = embed(epLink);
                        if (!safe.isEmpty() && seen.add(voiceName + "|" + safe)) {
                            Anime.Variant v = new Anime.Variant(voiceName, "Tsuyu", safe);
                            v.displayName = voiceName;
                            ep.variants.add(v);
                        }
                    }
                }
                if (ep.variants.isEmpty() && !defaultUrl.isEmpty() && (!isFuture || i == 1)) {
                    ep.resolverUrl = Uri.parse(defaultUrl).buildUpon().appendQueryParameter("episode", String.valueOf(i)).build().toString();
                    ep.variants.add(new Anime.Variant("Оригинал / Плеер Tsuyu", "Tsuyu", ep.resolverUrl));
                }
                if (ep.variants.isEmpty() && isFuture) {
                    ep.future = true;
                    ep.airDate = (i == aired + 1 && !shell.nextEpisodeAt.isEmpty()) ? shell.nextEpisodeAt : "Не вышла";
                    ep.name = ep.airDate;
                    ep.lazy = "future";
                } else {
                    ep.future = false;
                }
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
                s = u.buildUpon().scheme("https").authority(Sec.s("3f1c11101e-2309020b00-066506165f")).build().toString();
            }
            return ApiRepository.safeUrl(s);
        } catch (Exception e) {
            return "";
        }
    }
}
