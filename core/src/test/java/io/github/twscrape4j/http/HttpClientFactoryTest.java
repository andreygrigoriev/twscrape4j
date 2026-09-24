package io.github.twscrape4j.http;

import io.github.twscrape4j.accounts.Account;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import javax.net.ssl.SSLContext;
import java.security.Security;

import static org.junit.jupiter.api.Assertions.*;

class HttpClientFactoryTest {

    private final HttpClientFactory factory = new HttpClientFactory();

    static boolean conscryptAvailable() {
        return HttpClientFactory.CONSCRYPT_AVAILABLE;
    }

    private Account testAccount(String authToken, String ct0) {
        return new Account("user", "pass", "e@test.com", "epass",
                authToken, ct0, null, true, true, null, null, 0L);
    }

    @Test
    @EnabledIf("conscryptAvailable")
    void conscryptProviderIsRegistered() {
        assertNotNull(Security.getProvider("Conscrypt"), "Conscrypt provider must be registered");
    }

    @Test
    @EnabledIf("conscryptAvailable")
    void conscryptIsHighestPriorityProvider() {
        assertEquals("Conscrypt", Security.getProviders()[0].getName());
    }

    @Test
    @EnabledIf("conscryptAvailable")
    void sslContextUsesConscryptProvider() throws Exception {
        SSLContext ctx = SSLContext.getInstance("TLS", "Conscrypt");
        assertNotNull(ctx);
        assertEquals("TLS", ctx.getProtocol());
    }

    @Test
    void buildClientSucceedsWithValidTokens() {
        var account = testAccount("my_auth_token", "my_ct0");
        try (var client = factory.buildClient(account)) {
            assertNotNull(client);
        } catch (Exception e) {
            fail("Should not throw: " + e.getMessage());
        }
    }

    @Test
    void buildClientSucceedsWithNullProxy() {
        var account = testAccount("tok", "ct0");
        assertDoesNotThrow(() -> factory.buildClient(account).close());
    }

    @Test
    @EnabledIf("conscryptAvailable")
    void staticInitIsIdempotent() {
        new HttpClientFactory();
        new HttpClientFactory();
        long conscryptCount = java.util.Arrays.stream(Security.getProviders())
                .filter(p -> p.getName().equals("Conscrypt"))
                .count();
        assertEquals(1, conscryptCount);
    }

    @Test
    void buildClientWorksWithOrWithoutConscrypt() {
        // Verifies the factory doesn't blow up regardless of Conscrypt availability
        var account = testAccount("tok", "ct0val");
        assertDoesNotThrow(() -> {
            try (var c = factory.buildClient(account)) {
                assertNotNull(c);
            }
        });
    }
}
