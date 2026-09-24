package io.github.twscrape4j.cli;

import picocli.CommandLine.IVersionProvider;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/** Reads the project version from the Maven-filtered {@code version.properties} resource. */
public class VersionProvider implements IVersionProvider {

    static final String RESOURCE = "/io/github/twscrape4j/cli/version.properties";

    @Override
    public String[] getVersion() {
        return new String[]{"twscrape " + version()};
    }

    static String version() {
        try (InputStream in = VersionProvider.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return "unknown";
            }
            var props = new Properties();
            props.load(in);
            return props.getProperty("version", "unknown");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
