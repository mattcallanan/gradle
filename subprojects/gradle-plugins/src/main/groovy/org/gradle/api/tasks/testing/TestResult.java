/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.tasks.testing;

import java.io.Serializable;

/**
 * Describes a test result.
 */
public interface TestResult extends Serializable {
    public enum ResultType { SUCCESS, FAILURE, SKIPPED }
    /**
     * @return The type of result.  Generally one wants it to be SUCCESS!
     */
    public ResultType getResultType();

    /**
     * If the test failed with an exception, this will be the exception.  Some
     * test frameworks do not fail without an exception (JUnit), so in those cases
     * this method will never return null.  If the resultType is not FAILURE an IllegalStateException is thrown.
     * @return The exception, if any, logged for this test.  If none, a null is returned.
     * @throws IllegalStateException If the result type is anything other than FAILURE.
     */
    public Throwable getException(); // throws exception if type !=  FAILURE
}