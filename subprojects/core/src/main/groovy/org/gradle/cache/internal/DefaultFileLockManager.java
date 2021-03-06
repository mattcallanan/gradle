/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.cache.internal;

import org.gradle.process.internal.ExecHandleBuilder;
import org.gradle.util.GFileUtils;
import org.gradle.util.Jvm;
import org.gradle.util.UncheckedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArraySet;

public class DefaultFileLockManager implements FileLockManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultFileLockManager.class);
    private static final byte LOCK_PROTOCOL = 2;
    private static final int LOCK_TIMEOUT = 60000;
    private static final int STATE_REGION_SIZE = 2;
    private static final int STATE_REGION_POS = 0;
    private static final int INFORMATION_REGION_POS = STATE_REGION_POS + STATE_REGION_SIZE;
    private final Set<File> lockedFiles = new CopyOnWriteArraySet<File>();
    private final ProcessMetaDataProvider metaDataProvider;

    public DefaultFileLockManager(ProcessMetaDataProvider metaDataProvider) {
        this.metaDataProvider = metaDataProvider;
    }

    public FileLock lock(File target, LockMode mode, String displayName) {
        File canonicalTarget = GFileUtils.canonicalise(target);
        if (!lockedFiles.add(canonicalTarget)) {
            throw new IllegalStateException(String.format("Cannot lock %s as it has already been locked by this process.", displayName));
        }
        try {
            return new DefaultFileLock(canonicalTarget, mode, displayName);
        } catch (Throwable t) {
            lockedFiles.remove(canonicalTarget);
            throw UncheckedException.asUncheckedException(t);
        }
    }

    private class DefaultFileLock implements FileLock {
        private final File lockFile;
        private final RandomAccessFile lockFileAccess;
        private final File target;
        private final LockMode mode;
        private final String displayName;
        private java.nio.channels.FileLock lock;

        public DefaultFileLock(File target, LockMode mode, String displayName) throws Throwable {
            this.target = target;
            this.mode = mode;
            this.displayName = displayName;
            if (target.isDirectory()) {
                lockFile = new File(target, target.getName() + ".lock");
            } else {
                lockFile = new File(target.getParentFile(), target.getName() + ".lock");
            }
            lockFile.getParentFile().mkdirs();
            lockFile.createNewFile();
            lockFileAccess = new RandomAccessFile(lockFile, "rw");
            try {
                lock = lock(mode);
            } catch (Throwable t) {
                // Also releases any locks
                lockFileAccess.close();
                throw t;
            }
        }

        public boolean isLockFile(File file) {
            return file.equals(lockFile);
        }

        public boolean getUnlockedCleanly() {
            return readFromFile(new Callable<Boolean>() {
                public Boolean call() throws Exception {
                    lockFileAccess.seek(STATE_REGION_POS);
                    try {
                        if (lockFileAccess.readByte() != LOCK_PROTOCOL) {
                            throw new IllegalStateException(String.format("Unexpected lock protocol found in lock file '%s' for %s.", lockFile, displayName));
                        }
                        if (!lockFileAccess.readBoolean()) {
                            // Process has crashed while updating target file
                            return false;
                        }
                    } catch (EOFException e) {
                        // Process has crashed writing to lock file
                        return false;
                    }
                    return true;
                }
            });
        }

        public <T> T readFromFile(Callable<T> action) throws LockTimeoutException {
            try {
                return action.call();
            } catch (Exception e) {
                throw UncheckedException.asUncheckedException(e);
            }
        }

        public void writeToFile(Runnable action) {
            try {
                // TODO - need to escalate without releasing lock
                java.nio.channels.FileLock updateLock = null;
                if (mode != LockMode.Exclusive) {
                    lock.release();
                    lock = null;
                    updateLock = lock(LockMode.Exclusive);
                }
                try {
                    markDirty();
                    action.run();
                    markClean();
                } finally {
                    if (mode != LockMode.Exclusive) {
                        updateLock.release();
                        lock = lock(mode);
                    }
                }
            } catch (Throwable t) {
                throw UncheckedException.asUncheckedException(t);
            }
        }

        private void markClean() throws IOException {
            lockFileAccess.seek(STATE_REGION_POS);
            lockFileAccess.writeByte(LOCK_PROTOCOL);
            lockFileAccess.writeBoolean(true);
            assert lockFileAccess.getFilePointer() == STATE_REGION_SIZE + STATE_REGION_POS;
        }

        private void markDirty() throws IOException {
            lockFileAccess.seek(STATE_REGION_POS);
            lockFileAccess.writeByte(LOCK_PROTOCOL);
            lockFileAccess.writeBoolean(false);
            assert lockFileAccess.getFilePointer() == STATE_REGION_SIZE + STATE_REGION_POS;
        }

        public void close() {
            try {
                LOGGER.debug("Releasing lock on {}.", displayName);
                lockedFiles.remove(target);
                // Also releases any locks
                lockFileAccess.close();
            } catch (IOException e) {
                throw UncheckedException.asUncheckedException(e);
            }
        }

        private java.nio.channels.FileLock lock(FileLockManager.LockMode lockMode) throws Throwable {
            LOGGER.debug("Waiting to acquire {} lock on {}.", lockMode, displayName);
            long timeout = System.currentTimeMillis() + LOCK_TIMEOUT;

            // Lock the state region, with the requested mode
            java.nio.channels.FileLock stateRegionLock = lockStateRegion(lockMode, timeout);
            if (stateRegionLock == null) {
                // Can't acquire lock, get details of owner to include in the error message
                String ownerPid = "unknown";
                String ownerProcess = "unknown";
                java.nio.channels.FileLock informationRegionLock = lockInformationRegion(LockMode.Shared, timeout);
                if (informationRegionLock == null) {
                    LOGGER.debug("Could not lock information region for {}. Ignoring.", displayName);
                } else {
                    try {
                        if (lockFileAccess.length() < INFORMATION_REGION_POS) {
                            LOGGER.debug("Lock file for {} is too short to contain information region. Ignoring.", displayName);
                        } else {
                            lockFileAccess.seek(INFORMATION_REGION_POS);
                            ownerPid = lockFileAccess.readUTF();
                            ownerProcess = lockFileAccess.readUTF();
                        }
                    } finally {
                        informationRegionLock.release();
                    }
                }

                String extra = "";
                File jstack = Jvm.current().getExecutable("jstack");
                if (jstack.isFile() && ownerPid.matches("\\d+")) {
                    ByteArrayOutputStream outstr = new ByteArrayOutputStream();
                    ExecHandleBuilder builder = new ExecHandleBuilder();
                    builder.workingDir(new File(".").getAbsoluteFile());
                    builder.commandLine(jstack, ownerPid);
                    builder.setStandardOutput(outstr);
                    builder.setErrorOutput(outstr);
                    builder.build().start().waitForFinish();
                    extra = new String(outstr.toByteArray());
                }
                throw new LockTimeoutException(String.format("Timeout waiting to lock %s. It is currently in use by another Gradle instance.%nProcess: %s%nPID: %s%nLock file: %s%n%s",
                        displayName, ownerProcess, ownerPid, lockFile, extra));
            }

            if (!stateRegionLock.isShared()) {
                // We have an exclusive lock (whether we asked for it or not). Update the information region.
                // Acquire an exclusive lock on the region and write our details there
                try {
                    if (lockFileAccess.length() < INFORMATION_REGION_POS) {
                        // File did not exist, or was corrupted
                        markDirty();
                    }
                    java.nio.channels.FileLock informationRegionLock = lockInformationRegion(LockMode.Exclusive, timeout);
                    if (informationRegionLock == null) {
                        throw new IllegalStateException(String.format("Timeout waiting to lock the information region for lock %s", displayName));
                    }
                    try {
                        lockFileAccess.seek(INFORMATION_REGION_POS);
                        lockFileAccess.writeUTF(metaDataProvider.getProcessIdentifier());
                        lockFileAccess.writeUTF(metaDataProvider.getProcessDisplayName());
                    } finally {
                        informationRegionLock.release();
                    }
                } catch (Throwable t) {
                    stateRegionLock.release();
                    throw t;
                }
            }

            LOGGER.debug("Lock acquired.");
            return stateRegionLock;
        }

        private java.nio.channels.FileLock lockStateRegion(LockMode lockMode, long timeout) throws IOException, InterruptedException {
            return lockRegion(lockMode, timeout, STATE_REGION_POS, STATE_REGION_SIZE);
        }

        private java.nio.channels.FileLock lockInformationRegion(LockMode lockMode, long timeout) throws IOException, InterruptedException {
            return lockRegion(lockMode, timeout, INFORMATION_REGION_POS, Long.MAX_VALUE - INFORMATION_REGION_POS);
        }

        private java.nio.channels.FileLock lockRegion(FileLockManager.LockMode lockMode, long timeout, long start, long size) throws IOException, InterruptedException {
            do {
                java.nio.channels.FileLock fileLock = lockFileAccess.getChannel().tryLock(start, size, lockMode == LockMode.Shared);
                if (fileLock != null) {
                    return fileLock;
                }
                Thread.sleep(200L);
            } while (System.currentTimeMillis() < timeout);
            return null;
        }
    }
}
