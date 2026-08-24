# libfvad JNI

[![Maven Central](https://img.shields.io/maven-central/v/io.github.jvoice-project/libfvad-jni.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.jvoice-project/libfvad-jni)
[![CI](https://github.com/jvoice-project/libfvad-jni/actions/workflows/main.yml/badge.svg)](https://github.com/jvoice-project/libfvad-jni/actions/workflows/main.yml)
[![License](https://img.shields.io/github/license/jvoice-project/libfvad-jni.svg)](LICENSE)

A JNI wrapper for [libfvad](https://github.com/dpirch/libfvad), a voice activity detection engine (based on the WebRTC VAD engine).

## Platform Support

Java >= 17 is supported.
This library aims to support the following platforms:

| OS          | Architecture | Notes                               |
|-------------|--------------|-------------------------------------|
| **Windows** | x86_64       |                                     |
| **Linux**   | x86_64       | built on Ubuntu 20.04.6, GLIBC 2.31 |
| **Linux**   | arm64        | built on Ubuntu 20.04.6, GLIBC 2.31 |
| **Linux**   | armv7l       | built on Ubuntu 20.04.6, GLIBC 2.31 |
| **macOS**   | x86_64       | built for macOS 14 Sonoma and newer |
| **macOS**   | arm64        | built for macOS 14 Sonoma and newer |

The native binaries for those platforms are included in the distributed JAR.

## Usage

The package is distributed through [Maven Central](https://central.sonatype.com/artifact/io.github.jvoice-project/libfvad-jni):

### Maven

```xml
<dependency>
    <groupId>io.github.jvoice-project</groupId>
    <artifactId>libfvad-jni</artifactId>
    <!-- replace $version with a specific version -->
    <version>$version</version>
</dependency>
```

### Gradle

```gradle
repositories {
    mavenCentral()
}

dependencies {
    implementation 'io.github.jvoice-project:libfvad-jni:+' // gets the latest version
}
```

All releases are signed with the PGP key `047A5F7D27B9F2408F31EB6D577886B2F4A44CB7`,
you can find the public key on [keys.openpgp.org](https://keys.openpgp.org/search?q=047A5F7D27B9F2408F31EB6D577886B2F4A44CB7).
To import the public key, use the following command:

```shell
gpg --keyserver keys.openpgp.org --recv-keys 047A5F7D27B9F2408F31EB6D577886B2F4A44CB7
```

You can also find the package's jar attached to each [release](https://github.com/jvoice-project/libfvad-jni/releases).

### Example

```java
// Load the native library (only once)
VoiceActivityDetector.loadLibrary();

// Create detector instance
try (VoiceActivityDetector vad = VoiceActivityDetector.newInstance()) {
    int sampleRate = 16000;
    
    // Set VAD operating mode (QUALITY, LOW_BITRATE, AGGRESSIVE, VERY_AGGRESSIVE)
    vad.setMode(VoiceActivityDetector.Mode.QUALITY);
    vad.setSampleRate(VoiceActivityDetector.SampleRate.fromValue(sampleRate));
    
    short[] samples = ...; // Your 16-bit PCM audio samples
    int samplesLength = samples.length;
    int step = (sampleRate / 1000) * 10; // 10ms step (only 10ms, 20ms or 30ms frames are allowed)
    
    for (int i = 0; i < samplesLength - step; i += step) {
        short[] frame = Arrays.copyOfRange(samples, i, i + step);
        if (vad.process(frame)) {
            System.out.println("Voice detected at sample index: " + i);
            break;
        }
    }
}
```

## Development

You need to have Java >= 17 and C++ build tools installed.

After cloning the project, initialize the `libfvad` submodule by running:

```shell
git submodule update --init --recursive
```

### Build with Docker (Recommended)

You can build the native libraries for all supported Linux platforms (`amd64`, `arm64`, `arm/v7`) using Docker:

```shell
./build_linux-all.sh
```

This uses `docker buildx` to build the native libraries and places them in `src/main/resources`.

### Native Build (Local)

If you prefer to build locally for your current platform:

- **Linux:** Run `./build_linux.sh`
- **macOS:** Run `./build_macos.sh`
- **Windows:** Run `.\build_win.cmd`

These scripts compile the JNI shared library and copy it directly to the corresponding `src/main/resources` folder.

Finally, run the Maven verification to compile the Java project and execute the tests:

```shell
mvn verify
```

### Extending the Native API

If you want to add or modify native wrapper functions:

1. Add the native method definition in [`VoiceActivityDetector.java`](src/main/java/io/github/jvoiceproject/libfvadjni/VoiceActivityDetector.java).
2. Run the `gen_header.sh` script to regenerate the C++ JNI header [`io_github_jvoiceproject_libfvadjni_VoiceActivityDetector.h`](src/main/native/io_github_jvoiceproject_libfvadjni_VoiceActivityDetector.h).
3. Implement the native method in [`io_github_jvoiceproject_libfvadjni_VoiceActivityDetector.cpp`](src/main/native/io_github_jvoiceproject_libfvadjni_VoiceActivityDetector.cpp).
4. Run code formatting checking (`mvn spotless:check`) before submitting changes.
