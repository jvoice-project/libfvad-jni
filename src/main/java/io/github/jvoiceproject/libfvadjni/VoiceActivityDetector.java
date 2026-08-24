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

import io.github.jvoiceproject.libfvadjni.internal.NativeUtils;

import java.io.IOException;

/**
 * libfvad JNI
 *
 * <p>libfvad is a voice activity detection engine based on WebRTC's VAD engine.
 *
 * @author Miguel Álvarez Díez - Initial contribution
 */
public class VoiceActivityDetector implements AutoCloseable {

    private static boolean libraryLoaded;

    private final int pointerRef;
    private boolean closed;

    // region native api

    private static native int fvadNew();

    private native void fvadReset(int inst);

    private native int fvadSetMode(int inst, int mode);

    private native int fvadSetSampleRate(int inst, int sample_rate);

    private native int fvadProcess(int inst, short[] frame, int length);

    private native void fvadFree(int inst);

    // endregion

    private VoiceActivityDetector(int pointerRef) {
        this.pointerRef = pointerRef;
    }

    /**
     * Sets the VAD mode.
     *
     * @param mode the desired VAD mode
     * @return whether the operation was successful
     * @throws IOException when the instance is closed
     */
    public boolean setMode(Mode mode) throws IOException {
        assertOpen();
        return fvadSetMode(pointerRef, mode.ordinal()) == 0;
    }

    /**
     * Sets the VAD sampling rate.
     *
     * @param sampleRate desired audio sample rate
     * @return whether the operation was successful
     * @throws IOException when the instance is closed
     */
    public boolean setSampleRate(SampleRate sampleRate) throws IOException {
        assertOpen();
        return fvadSetSampleRate(pointerRef, sampleRate.toValue()) == 0;
    }

    /**
     * Process an audio frame.
     *
     * <p>Only audio frames with a length of 10, 20, or 30 ms are supported. Calculate the frame
     * length to use based on the {@code sampleRate/1000 * ms} formula.
     *
     * @param frame audio samples of the audio frame
     * @return whether voice activity was detected
     * @throws IOException when the instance is closed
     * @throws IllegalArgumentException when the frame length is incorrect
     */
    public boolean process(short[] frame) throws IOException, IllegalArgumentException {
        return process(frame, frame.length);
    }

    /**
     * Process an audio frame.
     *
     * @param frame audio samples of the audio frame
     * @param length audio frame length (number of samples)
     * @return whether voice activity was detected
     * @throws IOException when the instance is closed
     * @throws IllegalArgumentException when the frame length is incorrect
     */
    public boolean process(short[] frame, int length) throws IOException, IllegalArgumentException {
        assertOpen();
        var result = fvadProcess(pointerRef, frame, length);
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
    public void reset() throws IOException {
        assertOpen();
        fvadReset(pointerRef);
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            fvadFree(pointerRef);
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
        return new VoiceActivityDetector(fvadNew());
    }

    /**
     * Load the native library. Must be called before VAD instances can be created.
     *
     * @throws IOException when unable to load the native library
     */
    public static void loadLibrary() throws IOException {
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
        if (closed) {
            throw new IOException("VAD is closed.");
        }
    }
}
