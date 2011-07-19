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

package org.gradle.tooling.model.idea;

import java.io.File;
import java.util.List;

/**
 * @author: Szczepan Faber, created at: 7/19/11
 */
public interface IdeaModule {

    /**
     * @return the module name; not-<code>null</code>
     */
    String getName();

    /**
     * Allows to retrieve information about location of the module config file (*.iml) at the file system.
     * <p/>
     * Based on the following property -
     * <a href="http://www.gradle.org/current/docs/groovydoc/org/gradle/plugins/ide/idea/model/IdeaModuleIml.html#generateTo>IdeaModuleIml.generateTo</a>.
     *
     * @return directory where config file (*.iml) of the current module should be stored
     */
//  File getModuleFileLocation();

    /**
     * @return not-<code>null</code> instance of the project current module belongs to
     */
    IdeaProject getProject();

    /**
     * @return content root
     */
    File getContentRoot();

    /**
     * @return source dirs
     */
    List<File> getSourceDirectories();

    /**
     * @return test dirs
     */
    List<File> getTestDirectories();

    /**
     * @return test dirs
     */
    List<File> getExcludeDirectories();

    /**
     * Allows to answer if current module should use {@link IdeaProject#getOutputDir() project output directory}.
     * <p/>
     * Based on the following property -
     * <a href="http://www.gradle.org/current/docs/groovydoc/org/gradle/plugins/ide/idea/model/IdeaModule.html#inheritOutputDirs">IdeaModule.inheritOutputDirs</a>.
     *
     * @return information about whether current module should use {@link IdeaProject#getOutputDir() project output directory}. Default
     *            value it <code>'true'</code>
     * @see #getOutputDir()
     * @see #getTestOutputDir()
     */
//  boolean isInheritOutputDirs();

    /**
     * Allows to retrieve custom directory to store module's production classes and resources.
     * <p/>
     * Based on the following property -
     * <a href="http://www.gradle.org/current/docs/groovydoc/org/gradle/plugins/ide/idea/model/IdeaModule.html#outputDir">IdeaModule.outputDir</a>
     *
     * @return location of the directory to store production output. Is expected to be non-<code>null</code> if
     *            {@link #isInheritOutputDirs()} returns <code>'false'</code>
     */
//  File getOutputDir();

    /**
     * Allows to retrieve custom directory to store module's test classes and resources.
     * <p/>
     * Based on the following property -
     * <a href="http://www.gradle.org/current/docs/groovydoc/org/gradle/plugins/ide/idea/model/IdeaModule.html#testOutputDir">IdeaModule.testOutputDir</a>
     *
     * @return location of the directory to store test output. Is expected to be non-<code>null</code> if
     *            {@link #isInheritOutputDirs()} returns <code>'false'</code>
     */
//  File getTestOutputDir();

    /**
     * Allows to retrieve JDK to use with the current module.
     * <p/>
     * Based on the following property -
     * <a href="http://www.gradle.org/current/docs/groovydoc/org/gradle/plugins/ide/idea/model/Module.html#javaVersion>Module.javaVersion</a>.
     *
     * @return specific JDK to use with the current module; <code>null</code> as an indication that
     *            {@link IdeaProject#getJdk() project jdk} should be used
     */
//  IdeaJdk getJdk();

    /**
     * @return ordered collection of current module dependencies. Dependencies order affects module classpath
     */
//  DomainObjectSet<? extends IdeaDependency> getDependencies();
}
