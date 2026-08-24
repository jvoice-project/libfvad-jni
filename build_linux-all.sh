#!/bin/bash
set -e

# Ensure we are in the project root
cd "$(dirname "$0")"

echo "Building native libraries for all supported Linux platforms using Docker..."

# Initialize submodules if not already done
if [ ! -d "src/main/native/libfvad/src" ]; then
    echo "Initializing submodules..."
    git submodule update --init --recursive
fi

# Build for Linux amd64, arm64 and armv7l
docker buildx build \
    --platform linux/amd64,linux/arm64,linux/arm/v7 \
    --target export \
    --output "type=local,dest=src/main/resources/tmp" \
    .

# Ensure resource directories exist
mkdir -p src/main/resources/debian-amd64 src/main/resources/debian-arm64 src/main/resources/debian-armv7l
# Move binaries
cp src/main/resources/tmp/linux_amd64/*.so src/main/resources/debian-amd64/ 2>/dev/null || true
cp src/main/resources/tmp/linux_arm64/*.so src/main/resources/debian-arm64/ 2>/dev/null || true
cp src/main/resources/tmp/linux_arm_v7/*.so src/main/resources/debian-armv7l/ 2>/dev/null || true

rm -rf src/main/resources/tmp

echo "Build complete. Binaries are located in src/main/resources/"
ls -R src/main/resources/debian-*
