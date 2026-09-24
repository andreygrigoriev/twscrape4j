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
        return version(VersionProvider.class.getResourceAsStream(RESOURCE));
    }

    /** Reads {@code version} from {@code in} (closed afterwards); {@code "unknown"} when missing. */
    static String version(InputStream in) {
        if (in == null) {
            return "unknown";
        }
        try (in) {
            var props = new Properties();
            props.load(in);
            return props.getProperty("version", "unknown");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
