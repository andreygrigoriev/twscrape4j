package io.github.twscrape4j.cli;

import io.github.twscrape4j.cli.commands.LoginConverter;
import io.github.twscrape4j.cli.commands.NonBlankConverter;
import io.github.twscrape4j.cli.commands.PositiveLongConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import picocli.CommandLine;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
        String args = props.lines().filter(l -> !l.strip().startsWith("#")).reduce("", String::concat);
        for (String flag : new String[]{"--no-fallback", "-march=compatibility", "-H:+ReportExceptionStackTraces"}) {
            assertTrue(args.contains(flag), flag);
        }
        // Linux-only; added by the OS-activated native-linux profile so the native profile also builds elsewhere
        assertFalse(args.contains("--static-nolibc"), args);
        assertFalse(args.contains("--link-at-build-time"), args);
        assertFalse(args.contains("--initialize-at-build-time"), args);
    }

    @Test
    void staticNolibcComesFromLinuxActivatedProfile() throws IOException {
        // surefire runs with the module directory as working directory
        String pom = Files.readString(Path.of("pom.xml"));
        int profile = pom.indexOf("<id>native-linux</id>");
        assertTrue(profile > 0, "native-linux profile missing");
        String section = pom.substring(profile, pom.indexOf("</profile>", profile));
        assertTrue(section.contains("<family>linux</family>"), section);
        assertTrue(section.contains("<buildArg>--static-nolibc</buildArg>"), section);
    }

    @Test
    void picocliReflectConfigCoversCommandsAndConverters() throws IOException {
        // test-classes has its own generated config (for test commands) that shadows the main one on the classpath
        JsonNode root;
        try (InputStream in = Files.newInputStream(Path.of("target/classes", PICOCLI_DIR, "reflect-config.json"))) {
            root = MAPPER.readTree(in);
        }
        Set<String> classes = new HashSet<>();
        root.forEach(entry -> classes.add(entry.get("name").asString()));

        CommandLine cli = TwScrapeCli.newCommandLine(() -> null, new PrintWriter(new StringWriter()),
                new PrintWriter(new StringWriter()));
        Set<String> expected = new HashSet<>();
        expected.add(TwScrapeCli.class.getName());
        cli.getSubcommands().values().forEach(sub -> expected.add(sub.getCommand().getClass().getName()));
        expected.addAll(Set.of(
                OutputOptions.class.getName(),
                UserRef.Converter.class.getName(),
                LoginConverter.class.getName(),
                PositiveLongConverter.class.getName(),
                NonBlankConverter.class.getName()));
        assertEquals(14, expected.size() - 5, "13 subcommands plus the root");

        Set<String> missing = new HashSet<>(expected);
        missing.removeAll(classes);
        assertTrue(missing.isEmpty(), "missing from picocli reflect-config.json: " + missing);
    }

    private static URL resource(String name) {
        return NativeConfigTest.class.getClassLoader().getResource(name);
    }
}
