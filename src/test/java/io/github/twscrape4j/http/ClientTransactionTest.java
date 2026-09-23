package io.github.twscrape4j.http;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Base64;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Expected values were produced by running twscrape's reference {@code xclid.py} with the same inputs.
 */
class ClientTransactionTest {

    private static final List<Double> FRAME_A =
            doubles(200, 40, 90, 10, 180, 230, 120, 30, 210, 77, 15, 160, 245, 5);
    private static final List<Double> FRAME_B =
            doubles(0, 0, 0, 255, 255, 255, 255, 0, 0, 255, 255, 255, 0, 0);

    private static List<Double> doubles(int... values) {
        return IntStream.of(values).mapToObj(v -> (double) v).toList();
    }

    @Test
    void animationKeyMatchesReferenceImplementation() {
        assertEquals("c32c5e101999999999999a01999999999999a100", ClientTransaction.animationKey(FRAME_A, 0.3125));
        assertEquals("2020200b5c28f5c28f5c0b5c28f5c28f5c0b5c28f5c28f5c0b5c28f5c28f5c00",
                ClientTransaction.animationKey(FRAME_B, 0.5));
        assertEquals("c8285a100100", ClientTransaction.animationKey(FRAME_A, 0.0));
        assertEquals("ab4e60ee147ae147ae1805c28f5c28f5c2805c28f5c28f5c280ee147ae147ae1800",
                ClientTransaction.animationKey(FRAME_A, 1.0));
    }

    @ParameterizedTest
    @CsvSource({"0.5,.8", "0.87,.DEB851EB851EB8", "1.0,1", "0.25,.4", "0.13,.2147AE147AE148"})
    void floatToHexMatchesReferenceImplementation(double value, String expected) {
        assertEquals(expected, ClientTransaction.floatToHex(value));
    }

    @Test
    void generateMatchesReferenceImplementation() {
        int[] vk = IntStream.range(10, 58).toArray();
        var tx = new ClientTransaction(vk, "ab12cd00");

        String id = tx.generate("GET", "/i/api/graphql/hyPfJYJ_XAtDYoslQc-Rgg/SearchTimeline", 12345678L, 77);

        assertEquals("TUdGQUBDQl1cX15ZWFtaVVRXVlFQU1JtbG9uaWhramVkZ2ZhYGNifXx/fnl4e3p1dAMs8U0sXJoZlMBdpiKg9+eOHAltTg", id);
    }

    @Test
    void generateEmbedsVerificationKeyXoredWithRandomByte() {
        int[] vk = IntStream.range(0, 48).toArray();
        String id = new ClientTransaction(vk, "key").generate("GET", "/path");

        byte[] raw = Base64.getDecoder().decode(id);
        assertEquals(1 + 48 + 4 + 16 + 1, raw.length);
        int random = raw[0] & 0xFF;
        for (int k = 0; k < vk.length; k++) assertEquals(vk[k], (raw[k + 1] & 0xFF) ^ random);
        assertEquals(3, (raw[raw.length - 1] & 0xFF) ^ random);
        assertFalse(id.endsWith("="));
    }

    @Test
    void parsesVerificationKeyAndAnimationFrames() {
        String html = """
                <html><head><meta name="twitter-site-verification" content="AAECAwQBBgc="></head><body>
                <svg id="loading-x-anim-0"><g><path d="ignored"/><path d="M 10,30 C 1 2 3 4 5 6 7 8 9 10 11 12 13 14 C 20 21 22"/></g></svg>
                <svg id="loading-x-anim-1"><g><path d="ignored"/><path d="M 10,30 C 99 98"/></g></svg>
                </body></html>
                """;
        var doc = Jsoup.parse(html);

        int[] vk = ClientTransaction.parseVerificationKey(doc);
        assertArrayEquals(new int[]{0, 1, 2, 3, 4, 1, 6, 7}, vk);

        // vk[5] = 1 → second animation path
        var frames = ClientTransaction.parseAnimationFrames(doc, vk);
        assertEquals(List.of(List.of(99.0, 98.0)), frames);
    }

    @Test
    void missingVerificationKeyThrows() {
        var doc = Jsoup.parse("<html><head></head></html>");
        assertThrows(TwitterException.class, () -> ClientTransaction.parseVerificationKey(doc));
    }

    @Test
    void parsesSigningIndices() {
        String js = "x=(a[12],16)(b[3], 16);y=(c[40],16)";
        assertEquals(List.of(3, 40), ClientTransaction.parseIndices(js));
    }

    @Test
    void scriptUrlsFindsXWebAndLegacyChunks() {
        String html = """
                <link rel="modulepreload" href="https://abs.twimg.com/x-web/assets/entry-client-abc.js">
                <script>{12:"ondemand.s",12:"0123abcd0123abcd",99:"abcdef1"}</script>
                """;
        assertEquals(List.of(
                "https://abs.twimg.com/x-web/assets/entry-client-abc.js",
                "https://abs.twimg.com/responsive-web/client-web/ondemand.s.0123abcd0123abcda.js",
                "https://abs.twimg.com/responsive-web/client-web/99.abcdef1a.js"
        ), ClientTransaction.scriptUrls(html));
    }

    @Test
    void scriptUrlsDetectsCloudflareChallenge() {
        String html = "<script src=\"/cdn-cgi/challenge-platform/h/b/orchestrate/jsch/v1\"></script>";
        var e = assertThrows(TwitterException.class, () -> ClientTransaction.scriptUrls(html));
        assertTrue(e.getMessage().contains("Cloudflare"));
    }
}
