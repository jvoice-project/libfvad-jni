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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class VoiceActivityDetectorTest {
    Path samplePath = Path.of("jfk.wav");
    private VoiceActivityDetector vad;

    @BeforeEach
    public void before() throws IOException {
        var sampleFile = samplePath.toFile();
        if (!sampleFile.exists() || !sampleFile.isFile()) {
            throw new RuntimeException("Missing sample file");
        }
        VoiceActivityDetector.loadLibrary();
        vad = VoiceActivityDetector.newInstance();
    }

    @AfterEach
    public void after() throws IOException {
        vad.close();
    }

    @Test
    public void testSetMode() throws IOException {
        assertTrue(vad.setMode(VoiceActivityDetector.Mode.AGGRESSIVE), "vad mode configured");
    }

    @Test
    public void testSetSampleRate() throws IOException {
        assertNotNull(VoiceActivityDetector.SampleRate.fromValue(8000));
        assertNotNull(VoiceActivityDetector.SampleRate.fromValue(16000));
        assertNotNull(VoiceActivityDetector.SampleRate.fromValue(32000));
        assertNotNull(VoiceActivityDetector.SampleRate.fromValue(48000));
        assertThrows(
                IllegalArgumentException.class,
                () -> VoiceActivityDetector.SampleRate.fromValue(1));
        assertTrue(
                vad.setSampleRate(VoiceActivityDetector.SampleRate.S32000),
                "sample rate configured");
    }

    @Test
    public void testReset() throws IOException {
        vad.reset();
    }

    @Test
    public void testVAD() throws IOException, UnsupportedAudioFileException {
        int sampleRate = 16000;
        vad.setMode(VoiceActivityDetector.Mode.QUALITY);
        vad.setSampleRate(VoiceActivityDetector.SampleRate.fromValue(sampleRate));
        short[] samples = readJFKFileSamples();
        int samplesLength = samples.length;
        int step = (sampleRate / 1000) * 10; // 10ms step (only allows 10, 20 or 30ms frame)
        int detection = 0;
        for (int i = 0; i < samplesLength - step; i += step) {
            short[] frame = Arrays.copyOfRange(samples, i, i + step);
            if (vad.process(frame)) {
                detection = i;
                break;
            }
        }
        assertEquals(640, detection);
    }

    @Test
    public void testConcurrentInstancesAndLifecycle() throws Exception {
        int threads = 16;
        int iterationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        short[] frame = Arrays.copyOfRange(readJFKFileSamples(), 0, 160); // 10ms frame at 16kHz

        try {
            for (int t = 0; t < threads; t++) {
                futures.add(
                        executor.submit(
                                () -> {
                                    startLatch.await();
                                    for (int i = 0; i < iterationsPerThread; i++) {
                                        VoiceActivityDetector detector =
                                                VoiceActivityDetector.newInstance();
                                        try {
                                            assertTrue(
                                                    detector.setMode(
                                                            VoiceActivityDetector.Mode.AGGRESSIVE));
                                            assertTrue(
                                                    detector.setSampleRate(
                                                            VoiceActivityDetector.SampleRate
                                                                    .S16000));
                                            detector.process(frame);
                                        } finally {
                                            detector.close();
                                        }
                                    }
                                    return null;
                                }));
            }
            startLatch.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdown();
            assertTrue(
                    executor.awaitTermination(5, TimeUnit.SECONDS),
                    "Executor should terminate cleanly");
        }
    }

    @Test
    public void testConcurrentLoadLibrary() throws Exception {
        int threads = 16;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int t = 0; t < threads; t++) {
                futures.add(
                        executor.submit(
                                () -> {
                                    startLatch.await();
                                    VoiceActivityDetector.loadLibrary();
                                    return null;
                                }));
            }
            startLatch.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdown();
            assertTrue(
                    executor.awaitTermination(5, TimeUnit.SECONDS),
                    "Executor should terminate cleanly");
        }
    }

    @Test
    public void testConcurrentClose() throws Exception {
        VoiceActivityDetector detector = VoiceActivityDetector.newInstance();
        int threads = 16;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int t = 0; t < threads; t++) {
                futures.add(
                        executor.submit(
                                () -> {
                                    startLatch.await();
                                    detector.close();
                                    return null;
                                }));
            }
            startLatch.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
            assertThrows(IOException.class, () -> detector.reset());
        } finally {
            executor.shutdown();
            assertTrue(
                    executor.awaitTermination(5, TimeUnit.SECONDS),
                    "Executor should terminate cleanly");
        }
    }

    @Test
    public void testConcurrentProcessOnSameInstance() throws Exception {
        try (VoiceActivityDetector detector = VoiceActivityDetector.newInstance()) {
            assertTrue(detector.setMode(VoiceActivityDetector.Mode.QUALITY));
            assertTrue(detector.setSampleRate(VoiceActivityDetector.SampleRate.S16000));
            short[] frame = Arrays.copyOfRange(readJFKFileSamples(), 0, 160);

            int threads = 8;
            int iterations = 100;
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch startLatch = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();

            try {
                for (int t = 0; t < threads; t++) {
                    futures.add(
                            executor.submit(
                                    () -> {
                                        startLatch.await();
                                        for (int i = 0; i < iterations; i++) {
                                            detector.process(frame);
                                        }
                                        return null;
                                    }));
                }
                startLatch.countDown();
                for (Future<?> future : futures) {
                    future.get(10, TimeUnit.SECONDS);
                }
            } finally {
                executor.shutdown();
                assertTrue(
                        executor.awaitTermination(5, TimeUnit.SECONDS),
                        "Executor should terminate cleanly");
            }
        }
    }

    @Test
    public void testConcurrentProcessAndClose() throws Exception {
        VoiceActivityDetector detector = VoiceActivityDetector.newInstance();
        detector.setMode(VoiceActivityDetector.Mode.QUALITY);
        detector.setSampleRate(VoiceActivityDetector.SampleRate.S16000);
        short[] frame = Arrays.copyOfRange(readJFKFileSamples(), 0, 160);

        int workerThreads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(workerThreads + 1);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int t = 0; t < workerThreads; t++) {
                futures.add(
                        executor.submit(
                                () -> {
                                    startLatch.await();
                                    try {
                                        for (int i = 0; i < 500; i++) {
                                            detector.process(frame);
                                        }
                                    } catch (IOException expected) {
                                        // Expected once closed
                                    }
                                    return null;
                                }));
            }
            futures.add(
                    executor.submit(
                            () -> {
                                startLatch.await();
                                Thread.sleep(2);
                                detector.close();
                                return null;
                            }));

            startLatch.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
            assertThrows(IOException.class, () -> detector.process(frame));
        } finally {
            executor.shutdown();
            assertTrue(
                    executor.awaitTermination(5, TimeUnit.SECONDS),
                    "Executor should terminate cleanly");
        }
    }

    private short[] readJFKFileSamples() throws UnsupportedAudioFileException, IOException {
        // sample is a 16 bit int 16000hz little endian wav file
        AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(samplePath.toFile());
        // read all the available data to a little endian capture buffer
        ByteBuffer captureBuffer = ByteBuffer.allocate(audioInputStream.available());
        captureBuffer.order(ByteOrder.LITTLE_ENDIAN);
        int read = audioInputStream.read(captureBuffer.array());
        if (read == -1) {
            throw new IOException("Empty file");
        }
        // obtain the 16 int audio samples, short type in java
        var shortBuffer = captureBuffer.asShortBuffer();
        short[] samples = new short[captureBuffer.capacity() / 2];
        var i = 0;
        while (shortBuffer.hasRemaining()) {
            samples[i++] = shortBuffer.get();
        }
        return samples;
    }
}
