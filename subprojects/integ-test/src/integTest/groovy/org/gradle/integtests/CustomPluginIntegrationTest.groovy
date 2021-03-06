/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.integtests

import org.gradle.integtests.fixtures.ArtifactBuilder
import org.gradle.integtests.fixtures.internal.AbstractIntegrationSpec

public class CustomPluginIntegrationTest extends AbstractIntegrationSpec {
    public void "can reference plugin in buildSrc by id"() {
        given:
        file('buildSrc/src/main/java/CustomPlugin.java') << '''
import org.gradle.api.*;
public class CustomPlugin implements Plugin<Project> {
    public void apply(Project p) { p.setProperty("prop", "value"); }
}
'''

        file('buildSrc/src/main/resources/META-INF/gradle-plugins/custom.properties') << '''
implementation-class=CustomPlugin
'''

        file('build.gradle') << '''
apply plugin: 'custom'
assert 'value' == prop
task test
'''

        expect:
        succeeds('test')
    }

    public void "can reference plugin in external jar by id"() {
        given:
        ArtifactBuilder builder = artifactBuilder()
        builder.sourceFile('CustomPlugin.java') << '''
import org.gradle.api.*;
public class CustomPlugin implements Plugin<Project> {
    public void apply(Project p) { p.setProperty("prop", "value"); }
}
'''
        builder.resourceFile('META-INF/gradle-plugins/custom.properties') << '''
implementation-class=CustomPlugin
'''
        builder.buildJar(file('external.jar'))

        and:
        file('build.gradle') << '''
buildscript {
    dependencies {
        classpath files('external.jar')
    }
}
apply plugin: 'custom'
assert 'value' == prop
task test
'''

        expect:
        succeeds('test')
    }

    public void "loads plugin in correct environment"() {
        given:
        def implClassName = 'com.google.common.collect.Multimap'
        ArtifactBuilder builder = artifactBuilder()
        builder.sourceFile('CustomPlugin.groovy') << """
import org.gradle.api.*
public class CustomPlugin implements Plugin<Project> {
    public void apply(Project p) {
        Project.class.classLoader.loadClass('${implClassName}')
        try {
            getClass().classLoader.loadClass('${implClassName}')
            assert false: 'should fail'
        } catch (ClassNotFoundException e) {
            // expected
        }
        p.task('test')
    }
}
"""
        builder.resourceFile('META-INF/gradle-plugins/custom.properties') << '''
implementation-class=CustomPlugin
'''
        builder.buildJar(file('external.jar'))

        and:
        file('build.gradle') << '''
buildscript {
    dependencies {
        classpath files('external.jar')
    }
}
task test
'''

        expect:
        succeeds('test')
    }

    def "can use java plugin from custom plugin and its integration tests"() {
        given:
        file('src/main/groovy/CustomPlugin.groovy') << """
import org.gradle.api.Project
import org.gradle.api.Plugin
class CustomPlugin implements Plugin<Project> {
    void apply(Project project) {
        project.apply plugin: 'java'
    }
}
        """

        file('src/test/groovy/CustomPluginTest.groovy') << """
import org.junit.Test
import org.gradle.testfixtures.ProjectBuilder
class CustomPluginTest {
    @Test
    public void test() {
        def project = ProjectBuilder.builder().build()

        project.apply plugin: 'java'

        assert project.sourceSets
    }
}
"""

        buildFile << """
apply plugin: 'groovy'
repositories { mavenCentral() }
dependencies {
    compile gradleApi()
    groovy localGroovy()
    testCompile 'junit:junit:4.8.2'
}
"""

        expect:
        succeeds('test')
    }
}
