#pragma once

#include <chrono>
#include <condition_variable>
#include <exception>
#include <iostream>
#include <memory>
#include <mutex>
#include <queue>
#include <string>
#include <thread>
#include <vector>

#include "exception.h"
#include "jni_support.h"
#include "logging.h"
#include "net_rubygrapefruit_platform_internal_jni_AbstractNativeFileEventFunctions.h"
#include "net_rubygrapefruit_platform_internal_jni_AbstractNativeFileEventFunctions_NativeFileWatcher.h"

using namespace std;

// Corresponds to values of FileWatchEvent.ChangeType
enum class ChangeType {
    CREATED,
    REMOVED,
    MODIFIED,
    INVALIDATED
};

#define IS_SET(flags, mask) (((flags) & (mask)) != 0)

struct InsufficientResourcesFileWatcherException : public FileWatcherException {
public:
    InsufficientResourcesFileWatcherException(const string& message);
};

class AbstractServer;

AbstractServer* getServer(JNIEnv* env, jobject javaServer);

class AbstractServer : public JniSupport {
public:
    AbstractServer(JNIEnv* env, jobject watcherCallback);
    virtual ~AbstractServer();

    virtual void initializeRunLoop() = 0;
    void executeRunLoop(JNIEnv* env);

    /**
     * Registers new watch point with the server for the given paths.
     */
    virtual void registerPaths(const vector<u16string>& paths) = 0;

    /**
     * Unregisters watch points with the server for the given paths.
     */
    virtual bool unregisterPaths(const vector<u16string>& paths) = 0;

    /**
     * Shuts the server down.
     */
    virtual void shutdownRunLoop() = 0;

    /**
     * Waits for the given timeout for the server to finsih terminating.
     */
    bool awaitTermination(long timeoutInMillis);

protected:
    virtual void runLoop() = 0;

    void reportChangeEvent(JNIEnv* env, ChangeType type, const u16string& path);
    void reportUnknownEvent(JNIEnv* env, const u16string& path);
    void reportOverflow(JNIEnv* env, const u16string& path);
    void reportFailure(JNIEnv* env, const exception& ex);

private:
    mutex terminationMutex;
    condition_variable terminationVariable;
    bool terminated = false;

    JniGlobalRef<jobject> watcherCallback;
    jmethodID watcherReportChangeEventMethod;
    jmethodID watcherReportUnknownEventMethod;
    jmethodID watcherReportOverflowMethod;
    jmethodID watcherReportFailureMethod;
};

class NativePlatformJniConstants : public JniSupport {
public:
    NativePlatformJniConstants(JavaVM* jvm);

    const JClass nativeExceptionClass;
};

extern NativePlatformJniConstants* nativePlatformJniConstants;

jobject wrapServer(JNIEnv* env, AbstractServer* server);

jobject rethrowAsJavaException(JNIEnv* env, const exception& e);
jobject rethrowAsJavaException(JNIEnv* env, const exception& e, jclass exceptionClass);
