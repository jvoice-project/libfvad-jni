/*
 * #%L
 * libfvad-jni
 * %%
 * Copyright (C) 2023 - 2026 Miguel Álvarez Díez & Contributors to libfvad-jni
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package io.github.jvoiceproject.libfvadjni;

import io.github.jvoiceproject.libfvadjni.internal.FvadBinding;
import io.github.jvoiceproject.libfvadjni.internal.NativeUtils;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * libfvad JNI
 *
 * <p>libfvad is a voice activity detection engine based on WebRTC's VAD engine.
 *
 * <p>This class is fully thread-safe. All methods are safe to be called concurrently. Note that for
 * optimal voice detection accuracy, an instance should typically be dedicated to a single
 * continuous audio stream so that the underlying adaptive noise model remains consistent.
 *
 * @author Miguel Álvarez Díez - Initial contribution
 * @author Florian Hotze - Make thread-safe
 */
public class VoiceActivityDetector implements AutoCloseable {

    private static volatile boolean libraryLoaded;

    private final long nativeHandle;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private VoiceActivityDetector(long nativeHandle) {
        this.nativeHandle = nativeHandle;
    }

    /**
     * Sets the VAD mode.
     *
     * @param mode the desired VAD mode
     * @return whether the operation was successful
     * @throws IOException when the instance is closed
     */
    public synchronized boolean setMode(Mode mode) throws IOException {
        assertOpen();
        return FvadBinding.fvadSetMode(nativeHandle, mode.ordinal()) == 0;
    }

    /**
     * Sets the VAD sampling rate.
     *
     * @param sampleRate desired audio sample rate
     * @return whether the operation was successful
     * @throws IOException when the instance is closed
     */
    public synchronized boolean setSampleRate(SampleRate sampleRate) throws IOException {
        assertOpen();
        return FvadBinding.fvadSetSampleRate(nativeHandle, sampleRate.toValue()) == 0;
    }

    /**
     * Process an audio frame.
     *
     * <p>Only audio frames with a length of 10, 20, or 30 ms are supported. Calculate the frame
     * length to use based on the {@code sampleRate/1000 * ms} formula.
     *
     * <p>Note: Callers must not concurrently modify the provided {@code frame} array while
     * processing is in progress.
     *
     * @param frame audio samples of the audio frame
     * @return whether voice activity was detected
     * @throws IOException when the instance is closed
     * @throws IllegalArgumentException when the frame length is incorrect
     */
    public synchronized boolean process(short[] frame)
            throws IOException, IllegalArgumentException {
        return process(frame, frame.length);
    }

    /**
     * Process an audio frame.
     *
     * <p>Note: Callers must not concurrently modify the provided {@code frame} array while
     * processing is in progress.
     *
     * @param frame audio samples of the audio frame
     * @param length audio frame length (number of samples)
     * @return whether voice activity was detected
     * @throws IOException when the instance is closed
     * @throws IllegalArgumentException when the frame length is incorrect
     */
    public synchronized boolean process(short[] frame, int length)
            throws IOException, IllegalArgumentException {
        assertOpen();
        Objects.requireNonNull(frame, "frame is null");

        var result = FvadBinding.fvadProcess(nativeHandle, frame, length);
        if (result == -1) {
            throw new IllegalArgumentException("Invalid frame length");
        }
        return result == 1;
    }

    /**
     * Resets the VAD state.
     *
     * @throws IOException when the instance is closed
     */
    public synchronized void reset() throws IOException {
        assertOpen();
        FvadBinding.fvadReset(nativeHandle);
    }

    @Override
    public synchronized void close() {
        if (closed.compareAndSet(false, true)) {
            FvadBinding.fvadFree(nativeHandle);
        }
    }

    /** Available sample rates. */
    public enum SampleRate {
        /** 8 kHz = 8000 samples/seconds */
        S8000(8000),
        /** 16 kHz = 16000 samples/seconds */
        S16000(16000),
        /** 32 kHz = 32000 samples/seconds */
        S32000(32000),
        /** 48 kHz = 48000 samples/seconds */
        S48000(48000);

        SampleRate(final int value) {
            this.value = value;
        }

        private final int value;

        /**
         * Get the sample rate as samples/second.
         *
         * @return samples/second
         */
        public int toValue() {
            return value;
        }

        /**
         * Initialize from samples/second.
         *
         * <p>Only 8000, 16000, 32000, 48000 are allowed.
         *
         * @param value desired samples per second
         * @return sample rate enum instance
         * @throws IllegalArgumentException when the requested sample rate is not supported
         */
        public static SampleRate fromValue(int value) throws IllegalArgumentException {
            return switch (value) {
                case 8000 -> S8000;
                case 16000 -> S16000;
                case 32000 -> S32000;
                case 48000 -> S48000;
                default -> throw new IllegalArgumentException("Unsupported sample rate.");
            };
        }
    }

    /** Available VAD modes. */
    public enum Mode {
        // Enum order must not be changed as the enum ordinal is used in native code.
        /** Least aggressive about filtering out non-speech */
        QUALITY,
        /** Least aggressive about filtering out non-speech */
        LOW_BITRATE,
        /** Restrictive in reporting speech */
        AGGRESSIVE,
        /** More restrictive in reporting speech */
        VERY_AGGRESSIVE,
    }

    /**
     * Initializes a {@link VoiceActivityDetector} instance.
     *
     * @return a new {@link VoiceActivityDetector}.
     * @throws IOException native library unavailable.
     */
    public static VoiceActivityDetector newInstance() throws IOException {
        assertRegistered();
        long handle = FvadBinding.fvadNew();
        if (handle == 0) {
            throw new IOException("Failed to create VAD instance.");
        }
        return new VoiceActivityDetector(handle);
    }

    /**
     * Load the native library. Must be called before VAD instances can be created.
     *
     * @throws IOException when unable to load the native library
     */
    public static synchronized void loadLibrary() throws IOException {
        if (libraryLoaded) {
            return;
        }
        String bundleLibraryPath = null;
        String osName = System.getProperty("os.name").toLowerCase();
        String osArch = System.getProperty("os.arch").toLowerCase();
        if (osName.contains("win")) {
            if (osArch.contains("amd64") || osArch.contains("x86_64")) {
                bundleLibraryPath = "/win-amd64/libfvad-jni.dll";
            }
        } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
            if (osArch.contains("amd64") || osArch.contains("x86_64")) {
                bundleLibraryPath = "/debian-amd64/liblibfvad-jni.so";
            } else if (osArch.contains("aarch64") || osArch.contains("arm64")) {
                bundleLibraryPath = "/debian-arm64/liblibfvad-jni.so";
            } else if (osArch.contains("armv7") || osArch.contains("arm")) {
                bundleLibraryPath = "/debian-armv7l/liblibfvad-jni.so";
            }
        } else if (osName.contains("mac") || osName.contains("darwin")) {
            if (osArch.contains("amd64") || osArch.contains("x86_64")) {
                bundleLibraryPath = "/macos-amd64/liblibfvad-jni.dylib";
            } else if (osArch.contains("aarch64") || osArch.contains("arm64")) {
                bundleLibraryPath = "/macos-arm64/liblibfvad-jni.dylib";
            }
        }
        if (bundleLibraryPath == null) {
            throw new java.io.IOException(
                    "libfvad-jni: Unsupported platform " + osName + " - " + osArch + ".");
        }
        NativeUtils.loadLibraryFromJar(bundleLibraryPath);
        libraryLoaded = true;
    }

    private static void assertRegistered() throws IOException {
        if (!libraryLoaded) {
            throw new IOException("Native library is unavailable.");
        }
    }

    private void assertOpen() throws IOException {
        if (closed.get()) {
            throw new IOException("VAD is closed.");
        }
    }
}
