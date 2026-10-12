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

  const int16_t *framePointer = env->GetShortArrayElements(frame, NULL);
  return fvad_process(vad, framePointer, length);
}

JNIEXPORT void JNICALL Java_io_github_jvoiceproject_libfvadjni_VoiceActivityDetector_fvadFree(
    JNIEnv *env, jobject /* thisObject */, jlong handle)
{
  Fvad *vad = getVad(env, handle);
  if (vad) {
    fvad_free(vad);
  }
}
