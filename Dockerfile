# syntax=docker/dockerfile:1
#
# Native twscrape CLI image.
#   docker build -t twscrape4j-cli .
#   docker run --rm -e TWSCRAPE_AUTH_TOKEN=... -e TWSCRAPE_CT0=... twscrape4j-cli search "java" --limit 5
#
# The image arch follows the build host (arm64 on Apple Silicon). On hosts with little memory,
# cap the native-image builder heap, e.g. --build-arg NATIVE_IMAGE_OPTIONS=-J-Xmx2800m

# ---------------------------------------------------------------------------------------------
# Builder: compile the native executable and smoke-test it
# ---------------------------------------------------------------------------------------------
FROM ghcr.io/graalvm/native-image-community:25 AS builder

# mvnw (only-script distribution) needs unzip to unpack Maven; curl is already in the image.
RUN microdnf install -y tar gzip unzip && microdnf clean all

WORKDIR /src
COPY . .

# Extra native-image options (read by native-image itself), e.g. -J-Xmx2800m on small hosts.
ARG NATIVE_IMAGE_OPTIONS=""
ENV NATIVE_IMAGE_OPTIONS=${NATIVE_IMAGE_OPTIONS}

RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -Pnative -pl cli -am package -DskipTests

# Smoke checks: any failure breaks the image build.
RUN set -eu; B=/src/cli/target/twscrape; \
    echo '--- --help'; \
    "$B" --help > /dev/null; \
    echo '--- --version'; \
    "$B" --version | tee /tmp/version.txt; \
    grep -Eq '^twscrape [0-9]+\.[0-9]+\.[0-9]+' /tmp/version.txt; \
    echo '--- missing credentials -> exit 3'; \
    rc=0; env -i "$B" search foo > /dev/null 2> /tmp/err.txt || rc=$?; \
    cat /tmp/err.txt; \
    [ "$rc" -eq 3 ] || { echo "expected exit 3, got $rc"; exit 1; }; \
    grep -q 'TWSCRAPE_AUTH_TOKEN' /tmp/err.txt; \
    echo '--- ldd (only glibc libs expected)'; \
    ldd "$B" | tee /tmp/ldd.txt; \
    if grep -q 'not found' /tmp/ldd.txt; then echo 'unresolved shared library'; exit 1; fi

# No network: exercises HttpClientFactory static init (Conscrypt fallback) and JDK TLS setup.
RUN --network=none set -eu; B=/src/cli/target/twscrape; \
    echo '--- no network -> exit 1'; \
    rc=0; env -i TWSCRAPE_AUTH_TOKEN=x TWSCRAPE_CT0=y "$B" user @jack > /dev/null 2> /tmp/err.txt || rc=$?; \
    cat /tmp/err.txt; \
    [ "$rc" -eq 1 ] || { echo "expected exit 1, got $rc"; exit 1; }

# ---------------------------------------------------------------------------------------------
# Runtime: distroless glibc + CA certificates, non-root, no shell
# ---------------------------------------------------------------------------------------------
FROM gcr.io/distroless/base-debian12:nonroot

ARG VERSION=0.1.0-SNAPSHOT
ARG SOURCE=https://github.com/twscrape4j/twscrape4j
ARG LICENSES=NOASSERTION

LABEL org.opencontainers.image.title="twscrape4j-cli" \
      org.opencontainers.image.description="Stateless twscrape command-line tool (GraalVM native)" \
      org.opencontainers.image.source="${SOURCE}" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.licenses="${LICENSES}"

COPY --from=builder /src/cli/target/twscrape /usr/local/bin/twscrape

ENTRYPOINT ["/usr/local/bin/twscrape"]
