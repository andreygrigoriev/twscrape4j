package io.github.twscrape4j.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JVM-side checks for the native-image configuration. The actual native compile runs in the Docker build.
 */
class NativeConfigTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final String CONFIG_DIR = "META-INF/native-image/io.github.twscrape4j/twscrape4j-cli/";
    private static final String PICOCLI_DIR = "META-INF/native-image/picocli-generated/io.github.twscrape4j/twscrape4j-cli/";
    private static final Set<String> RESOURCES = Set.of(
            "io/github/twscrape4j/cli/version.properties",
            "simplelogger.properties",
            "org/publicsuffix/list/effective_tld_names.dat",
            "org/apache/hc/client5/version.properties");

    @ParameterizedTest
    @ValueSource(strings = {
            "io/github/twscrape4j/cli/version.properties",
            "simplelogger.properties",
            "org/publicsuffix/list/effective_tld_names.dat",
            "org/apache/hc/client5/version.properties"})
    void resourceIsOnClasspath(String name) {
        assertNotNull(resource(name), name);
    }

    @Test
    void reachabilityMetadataListsAllResources() throws IOException {
        JsonNode root;
        try (InputStream in = resource(CONFIG_DIR + "reachability-metadata.json").openStream()) {
            root = MAPPER.readTree(in);
        }
        Set<String> globs = new HashSet<>();
        root.get("resources").forEach(r -> globs.add(r.get("glob").asString()));
        assertEquals(RESOURCES, globs);
    }

    @Test
    void nativeImagePropertiesHaveRequiredFlagsOnly() throws IOException {
        String props;
        try (InputStream in = resource(CONFIG_DIR + "native-image.properties").openStream()) {
            props = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (String flag : new String[]{"--no-fallback", "--static-nolibc", "-march=compatibility",
                "-H:+ReportExceptionStackTraces"}) {
            assertTrue(props.contains(flag), flag);
        }
        String args = props.lines().filter(l -> !l.strip().startsWith("#")).reduce("", String::concat);
        assertFalse(args.contains("--link-at-build-time"), args);
        assertFalse(args.contains("--initialize-at-build-time"), args);
    }

    @Test
    void picocliReflectConfigIsGenerated() {
        assertNotNull(resource(PICOCLI_DIR + "reflect-config.json"));
    }

    private static URL resource(String name) {
        return NativeConfigTest.class.getClassLoader().getResource(name);
    }
}
