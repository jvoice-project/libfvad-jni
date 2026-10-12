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
package io.github.jvoiceproject.libfvadjni.internal;

/**
 * JNI binding for libfvad.
 *
 * @see <a
 *     href="https://github.com/dpirch/libfvad/blob/997e9dc85bcf5a8be27b0b5b422c7c260c667a41/include/fvad.h">fvad.h</a>
 * @author Florian Hotze - Initial contribution
 */
public class FvadBinding {
    /**
     * Creates and initializes a new VAD instance.
     *
     * <p>On success, returns the pointer to the new VAD instance, which should eventually be
     * deleted using {@link #fvadFree(long)}.
     *
     * @return pointer to the instance
     */
    public static native long fvadNew();

    /**
     * Reinitializes a VAD instance, clearing all state and resetting mode and sample rate to
     * defaults.
     *
     * @param handle pointer to the instance
     */
    public static native void fvadReset(long handle);

    /**
     * Changes the VAD operating ("aggressiveness") mode of a VAD instance.
     *
     * <p>A more aggressive (higher mode) VAD is more restrictive in reporting speech. Put in other
     * words the probability of being speech when the VAD returns 1 is increased with increasing
     * mode. As a consequence also the missed detection rate goes up.
     *
     * <p>Valid modes are 0 ("quality"), 1 ("low bitrate"), 2 ("aggressive"), and 3 ("very
     * aggressive"). The default mode is 0.
     *
     * @param handle pointer to the instance
     * @param mode the desired mode
     * @return 0 on success, -1 on failure
     */
    public static native int fvadSetMode(long handle, int mode);

    /**
     * Sets the input sample rate in Hz for a VAD instance.
     *
     * <p>Valid values are 8000, 16000, 32000 and 48000. The default is 8000. Note that internally
     * all processing will be done 8000 Hz; input data in higher sample rates will just be
     * downsampled first.
     *
     * @param handle pointer to the instance
     * @param sample_rate the desired sample rate
     * @return 0 on success, -1 on failure
     */
    public static native int fvadSetSampleRate(long handle, int sample_rate);

    /**
     * Calculates a VAD decision for an audio frame.
     *
     * <p>`frame` is an array of `length` signed 16-bit samples. Only frames with a length of 10,
     * 20, or 30 ms are supported, so for example at 8 kHz.
     *
     * @param handle pointer to the instance
     * @param frame signed PCM 16-bit samples
     * @param length frame length in samples; in {80, 160, 240}
     * @return 1 if speech, 0 if not speech, -1 on error
     */
    public static native int fvadProcess(long handle, short[] frame, int length);

    /**
     * Frees the dynamic memory of a specific VAD instance.
     *
     * @param handle pointer to the instance
     */
    public static native void fvadFree(long handle);
}
