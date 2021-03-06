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
package org.gradle.api.internal.artifacts.ivyservice;

import org.apache.ivy.core.settings.IvySettings;
import org.gradle.cache.internal.FileLock;
import org.gradle.cache.internal.FileLockManager;
import org.gradle.util.UncheckedException;
import org.jfrog.wharf.ivy.lock.LockHolder;
import org.jfrog.wharf.ivy.lock.LockHolderFactory;
import org.jfrog.wharf.ivy.lock.LockLogger;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

public class DefaultCacheLockingManager implements LockHolderFactory, CacheLockingManager {
    private final ArtifactCacheMetaData cacheMetaData;
    private final FileLockManager fileLockManager;
    private final AtomicBoolean locked = new AtomicBoolean();

    public DefaultCacheLockingManager(FileLockManager fileLockManager, ArtifactCacheMetaData cacheMetaData) {
        this.fileLockManager = fileLockManager;
        this.cacheMetaData = cacheMetaData;
    }

    public <T> T withCacheLock(Callable<? extends T> action) {
        boolean wasUnlocked = locked.compareAndSet(false, true);
        if (!wasUnlocked) {
            throw new IllegalStateException("Cannot lock the artifact cache, as it is already locked by this process.");
        }
        try {
            FileLock lock = fileLockManager.lock(cacheMetaData.getCacheDir(), FileLockManager.LockMode.Exclusive, "artifact cache");
            try {
                return action.call();
            } catch (Exception e) {
                throw UncheckedException.asUncheckedException(e);
            } finally {
                lock.close();
            }
        } finally {
            locked.set(false);
        }
    }

    public LockLogger getLogger() {
        throw new UnsupportedOperationException();
    }

    public long getTimeoutInMs() {
        throw new UnsupportedOperationException();
    }

    public long getSleepTimeInMs() {
        throw new UnsupportedOperationException();
    }

    public String getLockFileSuffix() {
        throw new UnsupportedOperationException();
    }

    public LockHolder getLockHolder(final File protectedFile) {
        return new LockHolder() {
            public void releaseLock() {
            }

            public boolean acquireLock() {
                if (!locked.get()) {
                    throw new IllegalStateException("Cannot acquire artifact lock, as the artifact cache is not locked by this process.");
                }
                protectedFile.getParentFile().mkdirs();
                return true;
            }

            public File getLockFile() {
                throw new UnsupportedOperationException();
            }

            public File getProtectedFile() {
                return protectedFile;
            }

            public String stateMessage() {
                return "ok";
            }
        };
    }

    public LockHolder getOrCreateLockHolder(File protectedFile) {
        return getLockHolder(protectedFile);
    }

    public void close() throws IOException {
    }

    public void setSettings(IvySettings settings) {
        throw new UnsupportedOperationException();
    }

}
