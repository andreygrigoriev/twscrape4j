package io.github.twscrape4j.auth;

import io.github.twscrape4j.accounts.Account;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LoginClientTest {

    @Test
    void cookieAccountFactoryCreatesValidAccount() {
        Account account = CookieAccountFactory.fromCookies("alice", "myauthtoken", "myct0");
        assertEquals("alice", account.username());
        assertEquals("myauthtoken", account.authToken());
        assertEquals("myct0", account.ct0());
        assertTrue(account.active());
        assertTrue(account.loggedIn());
    }

    @Test
    void cookieAccountFactoryRejectsBlankUsername() {
        assertThrows(IllegalArgumentException.class,
                () -> CookieAccountFactory.fromCookies("", "tok", "ct0"));
    }

    @Test
    void cookieAccountFactoryRejectsBlankAuthToken() {
        assertThrows(IllegalArgumentException.class,
                () -> CookieAccountFactory.fromCookies("alice", "", "ct0"));
    }

    @Test
    void cookieAccountFactoryRejectsBlankCt0() {
        assertThrows(IllegalArgumentException.class,
                () -> CookieAccountFactory.fromCookies("alice", "tok", ""));
    }

    @Test
    void cookieAccountFactoryRejectsNullValues() {
        assertThrows(IllegalArgumentException.class,
                () -> CookieAccountFactory.fromCookies(null, "tok", "ct0"));
    }

    @Test
    void challengeHandlerStdinExists() {
        ChallengeHandler handler = ChallengeHandler.stdin();
        assertNotNull(handler);
    }

    @Test
    void parseCookiesExtractsKeyValuePairs() {
        String header = "auth_token=abc123; Path=/; Domain=.x.com; Secure";
        Map<String, String> cookies = LoginClient.parseCookies(header);
        assertEquals("abc123", cookies.get("auth_token"));
    }

    @Test
    void parseCookiesHandlesMultipleValues() {
        String header = "ct0=xyztoken; auth_token=tok456";
        Map<String, String> cookies = LoginClient.parseCookies(header);
        assertEquals("xyztoken", cookies.get("ct0"));
        assertEquals("tok456", cookies.get("auth_token"));
    }
}
