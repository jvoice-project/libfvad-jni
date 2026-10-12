#include <cstdint>
#include "fvad.h"
#include "io_github_jvoiceproject_libfvadjni_VoiceActivityDetector.h"

static Fvad *getVad(JNIEnv *env, jlong handle)
{
  if (handle == 0) {
    jclass ise = env->FindClass("java/lang/IllegalStateException");
    if (ise) {
      env->ThrowNew(ise, "Invalid or closed VAD handle");
      env->DeleteLocalRef(ise);
    }
    return nullptr;
  }
  return reinterpret_cast<Fvad *>(static_cast<uintptr_t>(handle));
}

JNIEXPORT jlong JNICALL Java_io_github_jvoiceproject_libfvadjni_VoiceActivityDetector_fvadNew(
    JNIEnv *env, jclass /* thisClass */)
{
  Fvad *vad = fvad_new();
  if (!vad) {
    jclass oomClass = env->FindClass("java/lang/OutOfMemoryError");
    if (oomClass) {
      env->ThrowNew(oomClass, "Failed to allocate Fvad instance");
    }
    return 0;
  }
  return static_cast<jlong>(reinterpret_cast<uintptr_t>(vad));
}

JNIEXPORT void JNICALL Java_io_github_jvoiceproject_libfvadjni_VoiceActivityDetector_fvadReset(
    JNIEnv *env, jobject /* thisObject */, jlong handle)
{
  Fvad *vad = getVad(env, handle);
  if (!vad) {
    return;
  }
  fvad_reset(vad);
}

JNIEXPORT jint JNICALL Java_io_github_jvoiceproject_libfvadjni_VoiceActivityDetector_fvadSetMode(
    JNIEnv *env, jobject /* thisObject */, jlong handle, jint mode)
{
  Fvad *vad = getVad(env, handle);
  if (!vad) {
    return -1;
  }
  return fvad_set_mode(vad, mode);
}

JNIEXPORT jint JNICALL Java_io_github_jvoiceproject_libfvadjni_VoiceActivityDetector_fvadSetSampleRate(
    JNIEnv *env, jobject /* thisObject */, jlong handle, jint sampleRate)
{
  Fvad *vad = getVad(env, handle);
  if (!vad) {
    return -1;
  }
  return fvad_set_sample_rate(vad, sampleRate);
}

JNIEXPORT jint JNICALL Java_io_github_jvoiceproject_libfvadjni_VoiceActivityDetector_fvadProcess(
    JNIEnv *env, jobject /* thisObject */, jlong handle, jshortArray frame, jint length)
{
  Fvad *vad = getVad(env, handle);
  if (!vad) {
    return -1;
  }

  if (!frame) {
    jclass npe = env->FindClass("java/lang/NullPointerException");
    if (npe) {
      env->ThrowNew(npe, "frame array cannot be null");
      env->DeleteLocalRef(npe);
    }
    return -1;
  }

  // --- BEGIN JNI CRITICAL SECTION ---
  // GetPrimitiveArrayCritical requests direct, in-place access to the array elements,
  // typically pinning the buffer to avoid memory copies and native allocations.
  //
  // Strict JNI Critical Section Rules:
  // 1. DO NOT make any JNI calls between Get*Critical and Release*Critical.
  // 2. DO NOT perform blocking operations (no I/O, sleep, lock contention, or thread joins).
  // 3. Keep execution bounded and minimal: the JVM may temporarily suspend garbage
  //    collection or delay safepoints while this thread holds the critical lock.
  void *raw = env->GetPrimitiveArrayCritical(frame, nullptr);
  if (!raw) {
    return -1; // OutOfMemoryError is already pending in the JVM
  }

  // Safe to call inside the critical section: fvad_process is purely non-blocking,
  // pre-allocated math/DSP running in ~1-3 microseconds without any JNI or system calls.
  jint result = fvad_process(vad, static_cast<const int16_t *>(raw), static_cast<size_t>(length));

  // --- END JNI CRITICAL SECTION ---
  // Release the pin immediately. JNI_ABORT informs the JVM that native code only read
  // from the array, avoiding any redundant write-back memory synchronization to the Java heap.
  env->ReleasePrimitiveArrayCritical(frame, raw, JNI_ABORT);

  return result;
}

JNIEXPORT void JNICALL Java_io_github_jvoiceproject_libfvadjni_VoiceActivityDetector_fvadFree(
    JNIEnv *env, jobject /* thisObject */, jlong handle)
{
  Fvad *vad = getVad(env, handle);
  if (vad) {
    fvad_free(vad);
  }
}
