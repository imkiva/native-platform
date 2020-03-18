/*
 * Copyright 2020 the original author or authors.
 *
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
 */

package net.rubygrapefruit.platform.file

import net.rubygrapefruit.platform.internal.Platform
import spock.lang.Requires
import spock.lang.Timeout

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

import static java.util.concurrent.TimeUnit.MINUTES
import static java.util.concurrent.TimeUnit.SECONDS
import static net.rubygrapefruit.platform.file.FileWatcherCallback.Type.CREATED

@Requires({ Platform.current().macOs || Platform.current().linux || Platform.current().windows })
class FileEventFunctionsStressTest extends AbstractFileEventFunctionsTest {

    def "can be started and stopped many times"() {
        when:
        100.times { i ->
            startWatcher(rootDir)
            stopWatcher()
        }

        then:
        noExceptionThrown()
    }

    def "can stop and restart watching directory many times"() {
        given:
        def createdFile = new File(rootDir, "created.txt")
        startWatcher(rootDir)
        100.times {
            watcher.stopWatching(rootDir)
            watcher.startWatching(rootDir)
        }

        when:
        def expectedChanges = expectEvents event(CREATED, createdFile)
        createNewFile(createdFile)

        then:
        expectedChanges.await()
    }

    @Timeout(value = 5, unit = MINUTES)
    def "can start and stop watching directory while changes are being made to its contents"() {
        given:
        def numberOfParallelWritersPerWatchedDirectory = 10
        def numberOfWatchedDirectories = 10

        def callback = new FileWatcherCallback() {
            @Override
            void pathChanged(FileWatcherCallback.Type type, String path) {
                assert !path.empty
            }

            @Override
            void reportError(Throwable ex) {
                ex.printStackTrace()
                uncaughtFailureOnThread << ex
            }
        }

        expect:
        20.times { iteration ->
            def watchedDirectories = (1..numberOfWatchedDirectories).collect { new File(rootDir, "iteration-$iteration/watchedDir-$it") }
            watchedDirectories.each { assert it.mkdirs() }

            def executorService = Executors.newFixedThreadPool(numberOfParallelWritersPerWatchedDirectory * numberOfWatchedDirectories)
            def readyLatch = new CountDownLatch(numberOfParallelWritersPerWatchedDirectory * numberOfWatchedDirectories)
            def startModifyingLatch = new CountDownLatch(1)
            def watcher = startNewWatcher(callback)
            watchedDirectories.each { watchedDirectory ->
                numberOfParallelWritersPerWatchedDirectory.times { index ->
                    executorService.submit({ ->
                        def fileToChange = new File(watchedDirectory, "file${index}.txt")
                        readyLatch.countDown()
                        startModifyingLatch.await()
                        fileToChange.createNewFile()
                        500.times { modifyIndex ->
                            fileToChange << "Another change: $modifyIndex\n"
                        }
                    })
                }
            }
            executorService.shutdown()

            watchedDirectories.each {
                watcher.startWatching(it)
            }
            readyLatch.await()
            startModifyingLatch.countDown()
            Thread.sleep(500)
            watcher.close()

            assert executorService.awaitTermination(20, SECONDS)
            assert uncaughtFailureOnThread.empty
        }
    }

    @Requires({ Platform.current().linux })
    def "can stop watching many directories when they have been deleted"() {
        given:
        def watchedDirectoryDepth = 10

        List<File> watchedDirectories = []
        def watchedDir = new File(rootDir, "watchedDir")
        assert watchedDir.mkdir()
        watchedDirectories.add(watchedDir)
        List<File> previousRoots = [watchedDir]
        watchedDirectoryDepth.times { depth ->
            previousRoots = previousRoots.collectMany { root ->
                createSubdirs(root, 2)
            }
            watchedDirectories.addAll(previousRoots)
        }
        LOGGER.info("Watching ${watchedDirectories.size()} directories")

        def callback = new FileWatcherCallback() {
            @Override
            void pathChanged(FileWatcherCallback.Type type, String path) {
                assert !path.empty
            }

            @Override
            void reportError(Throwable ex) {
                ex.printStackTrace()
                uncaughtFailureOnThread << ex
            }
        }

        when:
        def watcher = startNewWatcher(callback)
        watchedDirectories.each {
            watcher.startWatching(it)
        }
        Thread.sleep(500)
        assert rootDir.deleteDir()

        then:
        watcher.close()
    }

    protected static List<File> createSubdirs(File root, int number) {
        List<File> dirs = []
        number.times { index ->
            def dir = new File(root, "dir${index}")
            assert dir.mkdir()
            dirs << dir
        }
        return dirs
    }
}

