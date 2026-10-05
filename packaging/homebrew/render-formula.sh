#!/usr/bin/env bash
# Renders the Homebrew formula for a release from the per-platform checksum files that the
# release workflow attaches to the GitHub Release (twscrape-<version>-<os>-<arch>.tar.gz.sha256).
#
#   packaging/homebrew/render-formula.sh <version> <dist-dir> > twscrape.rb
set -euo pipefail

if [ $# -ne 2 ]; then
    echo "usage: $0 <version> <dist-dir>" >&2
    exit 2
fi
version=$1
dist=$2
template="$(dirname "$0")/twscrape.rb.in"

sha() {
    local file="$dist/twscrape-$version-$1.tar.gz.sha256" hash
    if [ ! -f "$file" ]; then
        echo "missing checksum file: $file" >&2
        return 1
    fi
    hash=$(cut -d' ' -f1 "$file")
    if ! [[ $hash =~ ^[0-9a-f]{64}$ ]]; then
        echo "not a sha256 in $file: $hash" >&2
        return 1
    fi
    echo "$hash"
}

macos_arm64=$(sha macos-arm64)
macos_x86_64=$(sha macos-x86_64)
linux_arm64=$(sha linux-arm64)
linux_x86_64=$(sha linux-x86_64)

sed -e "s/@VERSION@/$version/g" \
    -e "s/@SHA256_MACOS_ARM64@/$macos_arm64/" \
    -e "s/@SHA256_MACOS_X86_64@/$macos_x86_64/" \
    -e "s/@SHA256_LINUX_ARM64@/$linux_arm64/" \
    -e "s/@SHA256_LINUX_X86_64@/$linux_x86_64/" \
    "$template"
