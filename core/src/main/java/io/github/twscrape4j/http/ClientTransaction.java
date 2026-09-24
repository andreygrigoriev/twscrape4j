package io.github.twscrape4j.http;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates the {@code x-client-transaction-id} header X requires on GraphQL requests.
 *
 * <p>Port of twscrape's {@code xclid.py}, which is in turn based on
 * <a href="https://github.com/iSarabjitDhiman/XClientTransaction">XClientTransaction</a> (MIT).
 * The key material (site verification key + animation frame) is scraped once from the X web app
 * using an authenticated session and then reused to sign each request's method and path.
 */
public class ClientTransaction {

    private static final String PAGE_URL = "https://x.com/tesla";
    private static final String MIGRATE_URL = "https://x.com/x/migrate";
    private static final long EPOCH_OFFSET_SECONDS = 1682924400L;
    private static final String DEFAULT_KEYWORD = "obfiowerehiring";
    private static final int DEFAULT_RANDOM = 3;

    private static final Pattern ASSET_URL_RE = Pattern.compile("https://[\\w.-]+/x-web/[\\w./-]+\\.js");
    private static final Pattern RESPONSIVE_WEB_URL_RE =
            Pattern.compile("https://[\\w.-]+/responsive-web/client-web/[\\w./-]+\\.js");
    private static final Pattern LEGACY_MAIN_RE = Pattern.compile("/client-web/main\\.([^.\"']+)\\.js");
    private static final Pattern LOGGED_OUT_ENTRY_RE =
            Pattern.compile("(?:^|/)entry-client-logged-out(?:[-.][^/?#]+)?\\.js(?:[?#].*)?$");
    private static final Pattern CHALLENGE_SCRIPT_RE = Pattern.compile("/cdn-cgi/challenge-platform/(?:scripts|h)/");
    private static final Pattern CHUNK_HASH_RE = Pattern.compile("(\\d+):\"([0-9a-f]{7}|[0-9a-f]{16})\"");
    private static final Pattern CHUNK_ANY_RE = Pattern.compile("(\\d+):\"([^\"]+)\"");
    private static final Pattern HEX_HASH_RE = Pattern.compile("[0-9a-f]{7}|[0-9a-f]{16}");
    // Legacy build links `ondemand.s.*.js`; current x-web build dynamically imports `sign.o-*.js`.
    private static final Pattern INDICES_FILE_RE =
            Pattern.compile("(?:\\.{0,2}/)?[\\w./-]*?\\b(?:ondemand\\.s|sign\\.o)[\\w.-]*\\.js");
    private static final Pattern INDICES_RE = Pattern.compile("(\\(\\w\\[(\\d{1,2})\\],\\s*16\\))+");

    private static final int MAX_CONCURRENT_SCRIPT_FETCHES = 16;
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final Random RANDOM = new SecureRandom();

    private final int[] vkBytes;
    private final String animKey;

    ClientTransaction(int[] vkBytes, String animKey) {
        this.vkBytes = vkBytes.clone();
        this.animKey = animKey;
    }

    /**
     * Scrapes the key material from the X web app. The client must carry an authenticated
     * session (auth_token/ct0 cookies) — the logged-out app does not contain the signing data.
     */
    public static ClientTransaction load(CloseableHttpClient http) {
        String html = fetchPageText(http, PAGE_URL);
        Document doc = Jsoup.parse(html);
        int[] vkBytes = parseVerificationKey(doc);
        List<Integer> animIdx = parseAnimationIndices(http, html);
        List<List<Double>> animArr = parseAnimationFrames(doc, vkBytes);
        return new ClientTransaction(vkBytes, animationKey(vkBytes, animIdx, animArr));
    }

    /** Computes the header value for a request with the given HTTP method and URL path. */
    public String generate(String method, String path) {
        long ts = Math.floorDiv(System.currentTimeMillis() - EPOCH_OFFSET_SECONDS * 1000, 1000);
        return generate(method, path, ts, RANDOM.nextInt(256));
    }

    String generate(String method, String path, long ts, int randomByte) {
        String payload = method.toUpperCase() + "!" + path + "!" + ts + DEFAULT_KEYWORD + animKey;
        byte[] hash = sha256(payload.getBytes(StandardCharsets.UTF_8));

        byte[] out = new byte[1 + vkBytes.length + 4 + 16 + 1];
        int i = 0;
        out[i++] = (byte) randomByte;
        for (int b : vkBytes) out[i++] = (byte) (b ^ randomByte);
        for (int k = 0; k < 4; k++) out[i++] = (byte) (((ts >> (k * 8)) & 0xFF) ^ randomByte);
        for (int k = 0; k < 16; k++) out[i++] = (byte) ((hash[k] & 0xFF) ^ randomByte);
        out[i] = (byte) (DEFAULT_RANDOM ^ randomByte);

        return Base64.getEncoder().withoutPadding().encodeToString(out);
    }

    // MARK: page loading

    static String fetchPageText(CloseableHttpClient http, String url) {
        String text = httpGet(http, url);
        if (!text.contains(">document.location =")) return text;

        String redirect = text.split("document.location = \"", 2)[1].split("\"", 2)[0];
        text = httpGet(http, redirect);
        if (!text.contains("action=\"https://x.com/x/migrate\" method=\"post\"")) return text;

        var form = new LinkedHashMap<String, String>();
        String[] inputs = text.split("<input");
        for (int k = 1; k < inputs.length; k++) {
            String name = inputs[k].split("name=\"", 2)[1].split("\"", 2)[0];
            String value = inputs[k].split("value=\"", 2)[1].split("\"", 2)[0];
            form.put(name, value);
        }
        var post = new HttpPost(MIGRATE_URL);
        post.setEntity(new StringEntity(MAPPER.writeValueAsString(form), ContentType.APPLICATION_JSON));
        return execute(http, post, MIGRATE_URL);
    }

    private static String httpGet(CloseableHttpClient http, String url) {
        return execute(http, new HttpGet(url), url);
    }

    private static String execute(CloseableHttpClient http,
                                  org.apache.hc.core5.http.ClassicHttpRequest request, String url) {
        try {
            return http.execute(request, response -> {
                int status = response.getCode();
                var entity = response.getEntity();
                String body = entity != null
                        ? new String(entity.getContent().readAllBytes(), StandardCharsets.UTF_8) : "";
                if (status < 200 || status >= 300) {
                    throw new TwitterException("HTTP " + status + " loading " + url);
                }
                return body;
            });
        } catch (TwitterException e) {
            throw e;
        } catch (Exception e) {
            throw new TwitterException("Failed to load " + url, e);
        }
    }

    // MARK: key material parsing

    static int[] parseVerificationKey(Document doc) {
        var meta = doc.selectFirst("meta[name=twitter-site-verification][content]");
        if (meta == null || meta.attr("content").isEmpty()) {
            throw new TwitterException("X verification key not found");
        }
        try {
            byte[] raw = Base64.getDecoder().decode(meta.attr("content"));
            int[] result = new int[raw.length];
            for (int k = 0; k < raw.length; k++) result[k] = raw[k] & 0xFF;
            return result;
        } catch (IllegalArgumentException e) {
            throw new TwitterException("Invalid X verification key", e);
        }
    }

    static List<List<Double>> parseAnimationFrames(Document doc, int[] vkBytes) {
        List<String> paths = doc.select("svg[id^=loading-x-anim] g:first-child path:nth-child(2)").stream()
                .map(el -> el.attr("d").strip())
                .toList();
        if (paths.isEmpty()) throw new TwitterException("Animation data not found");

        String path = paths.get(vkBytes[5] % paths.size());
        var frames = new ArrayList<List<Double>>();
        try {
            for (String segment : path.substring(9).split("C", -1)) {
                var row = new ArrayList<Double>();
                for (String num : segment.replaceAll("\\D+", " ").strip().split(" ")) {
                    if (!num.isEmpty()) row.add(Double.parseDouble(num));
                }
                frames.add(row);
            }
        } catch (RuntimeException e) {
            throw new TwitterException("Invalid animation data", e);
        }
        return frames;
    }

    static List<Integer> parseAnimationIndices(CloseableHttpClient http, String html) {
        List<String> allScripts = scriptUrls(html);
        List<String> xWebScripts = allScripts.stream().filter(u -> u.contains("/x-web/")).toList();
        if (xWebScripts.stream().anyMatch(u -> LOGGED_OUT_ENTRY_RE.matcher(u).find())) {
            throw new TwitterException("X served the logged-out web app — check the account cookies");
        }
        List<String> scripts = xWebScripts.isEmpty() ? allScripts : xWebScripts;

        String url = scripts.stream()
                .filter(u -> INDICES_FILE_RE.matcher(u).find())
                .findFirst()
                .orElseGet(() -> findIndicesUrl(http, scripts));

        List<Integer> indices = parseIndices(fetchPageText(http, url));
        if (indices.isEmpty()) throw new TwitterException("Signing indices not found");
        return indices;
    }

    static List<Integer> parseIndices(String js) {
        var indices = new ArrayList<Integer>();
        Matcher m = INDICES_RE.matcher(js);
        while (m.find()) indices.add(Integer.parseInt(m.group(2)));
        return indices;
    }

    /** Extracts all known script URL formats from the X homepage HTML. */
    static List<String> scriptUrls(String html) {
        var urls = new ArrayList<String>();
        ASSET_URL_RE.matcher(html).results().forEach(r -> urls.add(r.group()));
        RESPONSIVE_WEB_URL_RE.matcher(html).results().forEach(r -> urls.add(r.group()));
        Matcher main = LEGACY_MAIN_RE.matcher(html);
        if (main.find()) urls.add(legacyScriptUrl("main", main.group(1)));

        // Cloudflare interstitial: only the challenge script is present, no app bundles
        if (urls.isEmpty() && CHALLENGE_SCRIPT_RE.matcher(html).find()) {
            throw new TwitterException("Cloudflare challenge page served instead of X web app");
        }

        // Legacy webpack build: chunk id → hash map and chunk id → name map
        var hashMap = new LinkedHashMap<String, String>();
        CHUNK_HASH_RE.matcher(html).results().forEach(r -> hashMap.put(r.group(1), r.group(2)));
        var nameMap = new LinkedHashMap<String, String>();
        CHUNK_ANY_RE.matcher(html).results()
                .filter(r -> !HEX_HASH_RE.matcher(r.group(2)).matches())
                .forEach(r -> nameMap.put(r.group(1), r.group(2)));
        hashMap.forEach((id, hash) -> urls.add(legacyScriptUrl(nameMap.getOrDefault(id, id), hash + "a")));

        if (urls.isEmpty()) throw new TwitterException("X web scripts not found");
        return List.copyOf(new LinkedHashSet<>(urls));
    }

    private static String legacyScriptUrl(String name, String hash) {
        return "https://abs.twimg.com/responsive-web/client-web/" + name + "." + hash + ".js";
    }

    /**
     * The indices file is not linked from the page directly — it is dynamically imported from one
     * of the bundle chunks. Scans chunks concurrently and returns the first reference found.
     */
    private static String findIndicesUrl(CloseableHttpClient http, List<String> scripts) {
        var semaphore = new Semaphore(MAX_CONCURRENT_SCRIPT_FETCHES);
        List<Callable<String>> tasks = scripts.stream().<Callable<String>>map(url -> () -> {
            semaphore.acquire();
            String body;
            try {
                body = httpGet(http, url);
            } finally {
                semaphore.release();
            }
            Matcher m = INDICES_FILE_RE.matcher(body);
            if (!m.find()) throw new IllegalStateException("no signing script reference in " + url);
            return URI.create(url).resolve(m.group()).toString();
        }).toList();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            return executor.invokeAny(tasks);
        } catch (ExecutionException e) {
            throw new TwitterException("Signing script not found in " + scripts.size() + " X web assets", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TwitterException("Interrupted while loading X web assets", e);
        }
    }

    // MARK: animation key

    static String animationKey(int[] vkBytes, List<Integer> animIdx, List<List<Double>> animArr) {
        long frameTime = 1;
        for (int idx : animIdx.subList(1, animIdx.size())) frameTime *= vkBytes[idx] % 16;
        frameTime = (long) Math.floor(frameTime / 10.0 + 0.5) * 10; // JS Math.round to nearest 10

        List<Double> frameRow = animArr.get(vkBytes[animIdx.get(0)] % 16);
        return animationKey(frameRow, frameTime / 4096.0);
    }

    static String animationKey(List<Double> frames, double targetTime) {
        double[] fromColor = {frames.get(0), frames.get(1), frames.get(2), 1};
        double[] toColor = {frames.get(3), frames.get(4), frames.get(5), 1};
        double[] fromRotation = {0.0};
        double[] toRotation = {solve(frames.get(6), 60.0, 360.0, true)};

        List<Double> rest = frames.subList(7, frames.size());
        double[] curves = new double[rest.size()];
        for (int k = 0; k < curves.length; k++) {
            curves[k] = solve(rest.get(k), k % 2 == 1 ? -1.0 : 0.0, 1.0, false);
        }
        double val = cubicValue(curves, targetTime);

        double[] color = interpolate(fromColor, toColor, val);
        for (int k = 0; k < color.length; k++) color[k] = Math.max(0, Math.min(255, color[k]));
        double[] rotation = interpolate(fromRotation, toRotation, val);

        var sb = new StringBuilder();
        for (int k = 0; k < color.length - 1; k++) sb.append(Long.toHexString((long) Math.rint(color[k])));
        for (double value : rotationMatrix(rotation[0])) {
            double rounded = Math.abs(round2(value));
            String hex = floatToHex(rounded);
            if (hex.startsWith(".")) sb.append(("0" + hex).toLowerCase());
            else sb.append(hex.isEmpty() ? "0" : hex);
        }
        sb.append("00");
        return sb.toString().replaceAll("[.-]", "");
    }

    static double cubicValue(double[] c, double time) {
        if (time <= 0.0) {
            double startGradient = 0.0;
            if (c[0] > 0.0) startGradient = c[1] / c[0];
            else if (c[1] == 0.0 && c[2] > 0.0) startGradient = c[3] / c[2];
            return startGradient * time;
        }
        if (time >= 1.0) {
            double endGradient = 0.0;
            if (c[2] < 1.0) endGradient = (c[3] - 1.0) / (c[2] - 1.0);
            else if (c[2] == 1.0 && c[0] < 1.0) endGradient = (c[1] - 1.0) / (c[0] - 1.0);
            return 1.0 + endGradient * (time - 1.0);
        }

        double start = 0.0, end = 1.0, mid = 0.0;
        while (start < end) {
            mid = (start + end) / 2;
            double xEst = bezier(c[0], c[2], mid);
            if (Math.abs(time - xEst) < 0.00001) return bezier(c[1], c[3], mid);
            if (xEst < time) start = mid;
            else end = mid;
        }
        return bezier(c[1], c[3], mid);
    }

    private static double bezier(double a, double b, double m) {
        return 3.0 * a * (1 - m) * (1 - m) * m + 3.0 * b * (1 - m) * m * m + m * m * m;
    }

    private static double[] interpolate(double[] from, double[] to, double f) {
        double[] out = new double[from.length];
        for (int k = 0; k < from.length; k++) out[k] = from[k] * (1 - f) + to[k] * f;
        return out;
    }

    private static double[] rotationMatrix(double degrees) {
        double rad = Math.toRadians(degrees);
        return new double[]{Math.cos(rad), -Math.sin(rad), Math.sin(rad), Math.cos(rad)};
    }

    private static double solve(double value, double min, double max, boolean rounding) {
        double result = value * (max - min) / 255 + min;
        return rounding ? Math.floor(result) : round2(result);
    }

    /** Python's {@code round(x, 2)}: exact decimal value, half-even. */
    private static double round2(double value) {
        return new BigDecimal(value).setScale(2, RoundingMode.HALF_EVEN).doubleValue();
    }

    static String floatToHex(double x) {
        var sb = new StringBuilder();
        long quotient = (long) x;
        double fraction = x - quotient;

        while (quotient > 0) {
            quotient = (long) (x / 16);
            long remainder = (long) (x - quotient * 16.0);
            sb.insert(0, remainder > 9 ? (char) (remainder + 55) : (char) ('0' + remainder));
            x = quotient;
        }
        if (fraction == 0) return sb.toString();

        sb.append('.');
        while (fraction > 0) {
            fraction *= 16;
            long integer = (long) fraction;
            fraction -= integer;
            sb.append(integer > 9 ? (char) (integer + 55) : (char) ('0' + integer));
        }
        return sb.toString();
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
