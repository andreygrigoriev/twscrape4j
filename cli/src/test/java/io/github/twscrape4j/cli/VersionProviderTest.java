package io.github.twscrape4j.cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VersionProviderTest {

    @Test
    void missingResourceIsUnknown() {
        assertEquals("unknown", VersionProvider.version(null));
    }

    @Test
    void missingVersionKeyIsUnknown() {
        var in = new ByteArrayInputStream("other=1".getBytes(StandardCharsets.UTF_8));
        assertEquals("unknown", VersionProvider.version(in));
    }

    @Test
    void readsVersionKey() {
        var in = new ByteArrayInputStream("version=1.2.3".getBytes(StandardCharsets.UTF_8));
        assertEquals("1.2.3", VersionProvider.version(in));
    }
}
