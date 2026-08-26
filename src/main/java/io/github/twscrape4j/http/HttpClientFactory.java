package io.github.twscrape4j.http;

import io.github.twscrape4j.accounts.Account;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.cookie.BasicClientCookie;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.ssl.TLS;
import org.conscrypt.Conscrypt;

import javax.net.ssl.SSLContext;
import java.security.Security;

@Slf4j
public class HttpClientFactory {

    /** True when Conscrypt native library loaded successfully. */
    static final boolean CONSCRYPT_AVAILABLE;

    static {
        boolean available = false;
        if (Security.getProvider("Conscrypt") != null) {
            available = true;
        } else {
            try {
                Security.insertProviderAt(Conscrypt.newProvider(), 1);
                available = true;
                log.debug("Conscrypt TLS provider registered");
            } catch (Throwable t) {
                log.warn("Conscrypt unavailable, falling back to JDK TLS (fingerprinting disabled): {}", t.getMessage());
            }
        }
        CONSCRYPT_AVAILABLE = available;
    }

    public CloseableHttpClient buildClient(Account account) {
        var cookieStore = new BasicCookieStore();
        addCookie(cookieStore, "auth_token", account.authToken());
        addCookie(cookieStore, "ct0", account.ct0());

        SSLConnectionSocketFactory sslFactory = buildSslFactory();
        HttpClientConnectionManager connManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setSSLSocketFactory(sslFactory)
                .build();

        var builder = HttpClients.custom()
                .setDefaultCookieStore(cookieStore)
                .setConnectionManager(connManager);

        if (account.proxy() != null && !account.proxy().isBlank()) {
            try {
                builder.setProxy(HttpHost.create(account.proxy()));
            } catch (java.net.URISyntaxException e) {
                throw new IllegalArgumentException("Invalid proxy URI: " + account.proxy(), e);
            }
        }

        return builder.build();
    }

    private SSLConnectionSocketFactory buildSslFactory() {
        if (CONSCRYPT_AVAILABLE) {
            try {
                SSLContext sslContext = SSLContext.getInstance("TLS", "Conscrypt");
                sslContext.init(null, null, null);
                return SSLConnectionSocketFactoryBuilder.create()
                        .setSslContext(sslContext)
                        .setTlsVersions(TLS.V_1_3, TLS.V_1_2)
                        .build();
            } catch (Exception e) {
                log.warn("Failed to build Conscrypt SSLContext, falling back to JDK: {}", e.getMessage());
            }
        }
        // JDK default TLS
        return SSLConnectionSocketFactoryBuilder.create()
                .setTlsVersions(TLS.V_1_3, TLS.V_1_2)
                .build();
    }

    private void addCookie(BasicCookieStore store, String name, String value) {
        if (value == null || value.isBlank()) return;
        var cookie = new BasicClientCookie(name, value);
        cookie.setDomain(".x.com");
        cookie.setPath("/");
        cookie.setSecure(true);
        store.addCookie(cookie);
    }
}
