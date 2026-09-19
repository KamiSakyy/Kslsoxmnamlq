import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class HtvTest {
    public static void main(String[] a) throws Exception {
        Pattern HTV_CARD = Pattern.compile("\\\\*\"title\\\\*\":\\\\*\"([^\"\\\\]{3,120})\\\\*\",\\\\*\"titleSlug\\\\*\":\\\\*\"([a-z0-9-]+)\\\\*\",\\\\*\"titleId\\\\*\":\\\\*\"[^\\\\]+\\\\*\",\\\\*\"ep\\\\*\":(\\d+)[^{}]*?\\\\*\"cover\\\\*\":\\\\*\"(/uploads/[a-zA-Z0-9._/-]+)\\\\*\"[^{}]*?\\\\*\"embedUrl\\\\*\":\\\\*\"https://nhplayer\\.com/v/([A-Za-z0-9]+)/");
        String home = new String(Files.readAllBytes(Paths.get("c22-home.html")), "UTF-8");
        String ser = new String(Files.readAllBytes(Paths.get("c22-ser.html")), "UTF-8");
        LinkedHashMap<String, Integer> map = new LinkedHashMap<>();
        Matcher m = HTV_CARD.matcher(home);
        while (m.find()) map.put(m.group(2), map.containsKey(m.group(2)) ? map.get(m.group(2)) + 1 : 1);
        System.out.println("home titles: " + map.size());
        for (Map.Entry<String, Integer> e : map.entrySet()) System.out.println("  " + e.getKey() + " x" + e.getValue());
        LinkedHashMap<Integer, String> eps = new LinkedHashMap<>();
        Matcher m2 = HTV_CARD.matcher(ser);
        while (m2.find()) if ("shoujo-ramune".equals(m2.group(2))) eps.putIfAbsent(Integer.parseInt(m2.group(3)), "https://nhplayer.com/v/" + m2.group(5) + "/");
        System.out.println("series shoujo-ramune eps: " + eps);
        String b64 = "aHR0cHM6Ly9yMi4xaGFuaW1lLmNvbS9zaG91am8tcmFtdW5lLTcubXA0fDE3ODk2OTA4NzF8MDQzNmM3OWMyNzQ2ZmQxOA==";
        String dec = new String(Base64.getDecoder().decode(b64), "UTF-8");
        int bar = dec.indexOf('|');
        System.out.println("decoded mp4: " + (bar > 0 ? dec.substring(0, bar) : dec));
    }
}
