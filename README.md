JGit Build Number for Maven, Ant, and Gradle
============================================

Extracts Git metadata and a freely composable build number in pure Java without Git command-line tool. [https://github.com/elab/jgit-buildnumber](https://github.com/elab/jgit-buildnumber)

Current version | Compatibility                                | Published on [Maven Central](https://search.maven.org/search?q=g:com.labun.buildnumber)
----------------|----------------------------------------------|------------
2.5.0           | Java 8+ (tested up to Java 16)<br>Maven 3.3+ | (not released yet)

<!--
Available on Maven Central: [repo1.maven.org](http://repo1.maven.org/maven2/com/labun/buildnumber/) / [central.maven.org](http://central.maven.org/maven2/com/labun/buildnumber/) / [search.maven.org](https://search.maven.org/search?q=g:com.labun.buildnumber).
-->

Based on the [work of Alex Kasko](https://github.com/alx3apps/jgit-buildnumber) with [merged changes from other forks](https://github.com/elab/jgit-buildnumber/network). Thank you [guys](https://github.com/elab/jgit-buildnumber/graphs/contributors)! Additionally contains many new features, bug fixes, and performance improvements. <!-- The code has been almost completely rewritten. -->

We believe that *JGit Build Number* is the best plugin for its purpose, but you can also look at alternatives:
- [maven-git-commit-id-plugin](https://github.com/git-commit-id/maven-git-commit-id-plugin)
- original [Git buildnumber plugin for Maven and Ant based on JGit](https://github.com/alx3apps/jgit-buildnumber) 
- [Build Number Maven Plugin](https://www.mojohaus.org/buildnumber-maven-plugin/)

Say what you think, feedback is always welcome :) 

Contact: Eugen Labun <labun@gmx.net>

--------------------------------------------------

Contents:
- [About "Build Number"](#about-build-number)
- [Extracted properties](#extracted-properties)
- [Configuration](#configuration)
- [Usage in Maven](#usage-in-maven)
- [Usage in Ant](#usage-in-ant)
- [Usage in Gradle](#usage-in-gradle)
- [Development notes](#development-notes)
- [License information](#license-information)
- [Changelog](#changelog)

--------------------------------------------------


About "Build Number"
--------------------

Build number should identify the code state of the project from which it has been created. 
Particularly, it should *not* depend on: 
- where the build takes place (locally, build server);
- how many times the same project state has been build (i.e. no simple increment).

In our case, the __default BuildNumber__ looks like `v19.3351.ddda02b` and consists of:

- _human readable id_: tag name or branch name `v19`
  ```
  git describe --exact-match --tags HEAD # tag name
  git symbolic-ref -q HEAD # branch name
  ```

- _build incremental id_: commits count (closely resembles SVN revision number) `3351`
  ```
  git rev-list HEAD | wc -l
  ```

- _globally unique id_: commit SHA-1 `ddda02b`
  ```
  git rev-parse HEAD # revision
  git rev-parse --short HEAD # short revision
  ```

- _dirty flag_ (optional): inserted if there are differences between working-tree, index, and HEAD; the BuildNumber would look like `v19.3351.ddda02b-dirty`; 
you cannot trust the build in this case :)<br>
  ```
  git status
  ```

Instead of the Git CLI commands above, the pure Java [JGit](http://www.jgit.org/) API is used.
Note: The JGit output (intentionally) doesn't coincide exactly with the output of Git CLI. 
E.g. branch and tag names are returned without the `refs/...` prefix.

**The default BuildNumber can be easily redefined using extracted properties (see [buildNumberFormat](#buildNumberFormat)).**
 
All properties, including BuildNumber, are available in Maven, Ant, or Gradle build for the entire application.

__Execution time__ primarily depends on the complexity of Git repo (especially on the number of tags, followed by the number of commits) 
and whether you use a custom JS `buildNumberFormat` or not. Without custom `buildNumberFormat`, you should expect execution time of 0.5 - 1.5 s. 
Add 0.5 s. if custom `buildNumberFormat` is used.


### Extracted properties

Git metadata, build number, and build date (added for convenience) are published as following project properties:

property            | description
--------------------|----------------
git.revision        | HEAD SHA-1
git.shortRevision   | <a name="shortRevision"/>HEAD SHA-1 (abbreviated, see [shortRevisionLength](#shortRevisionLength))
git.dirty           | <a name="dirty"/> contains [dirtyValue](#dirtyValue) if differences exist between working-tree, index, and HEAD; empty string otherwise;<br>in [verbose](#verbose) mode, detailed info will be printed to log about the changes which caused the dirty status (very helpful if the problem occurs on a remote build server)
git.branch          | branch name; empty string for detached HEAD
git.tag             | HEAD tag name; empty string if no tags defined; multiple tags separated with `;`
git.nearestTag      | nearest tag name; empty string if no tags found; multiple tags (belonging to the same commit) are separated with `;`<br>Only the "counted" commits are looked for tags; see [countCommitsSince...](#countCommitsSince)
git.parent          | SHA-1 of the parent commit (`HEAD^`); multiple parents separated with `;`
git.shortParent     | <a name="shortParent"/>SHA-1 of the parent commit (`HEAD^`) (abbreviated, see [shortRevisionLength](#shortRevisionLength)); multiple parents separated with `;`
git.commitsCount    | commits count; -1 for a Git shallow clone; see [countCommitsSince...](#countCommitsSince)
git.authorDate      | <a name="authorDate"/>authored date of HEAD commit; see [gitDateFormat](#gitDateFormat), [dateFormatTimeZone](#dateFormatTimeZone)
git.commitDate      | <a name="commitDate"/>committed date of HEAD commit; see [gitDateFormat](#gitDateFormat), [dateFormatTimeZone](#dateFormatTimeZone)
git.describe        | result of JGit `describe` command ([long format](https://download.eclipse.org/jgit/site/5.12.0.202106070339-r/apidocs/org/eclipse/jgit/api/DescribeCommand.html#setLong-boolean-), all tags will be considered: annotated and lightweight (not annotated)); abbreviated commit hash if no tags found (see [setAlways(true)](https://download.eclipse.org/jgit/site/5.12.0.202106070339-r/apidocs/org/eclipse/jgit/api/DescribeCommand.html#setAlways-boolean-))
git.buildDateMillis | <a name="buildDateMillis"/>start time of plugin execution in milliseconds, as returned by `System.currentTimeMillis()`
git.buildDate       | <a name="buildDate"/>start time of plugin execution, created from [buildDateMillis](#buildDateMillis) and formatted according to [buildDateFormat](#buildDateFormat), [dateFormatTimeZone](#dateFormatTimeZone)
git.buildNumber     | <a name="buildNumber"/>composed from other properties according to [buildNumberFormat](#buildNumberFormat) parameter 

Note that you can redefine the default namespace `git` using [namespace](#namespace) parameter.

You can see the extracted properties, the execution time, and other info in the build log. Set the [verbose](#verbose) parameter to `true` to achieve that.

Extracted properties can be accessed in the same way in all build tools: as `git.buildNumber` or `${git.buildNumber}`. 
See examples in sections for [Maven](#usage-in-maven), [Ant](#usage-in-ant), [Gradle](#usage-in-gradle).


### Configuration

We follow a zero configuration approach. Therefore all parameters are optional.
But just in the case you would like to tweak something, there is a lot of possibilities to do that:

parameter                                                    | description
-------------------------------------------------------------|----------------------------------------------
namespace                                                    | <a name="namespace"/>Properties are published with this namespace prefix. You may want to redefine the default value:<ul><li>to avoid name clashes with other plugins;<li>to extract properties for multiple Git repos (use multiple plugin/task executions with different namespaces for that).</ul>The value must be a valid [Java name](https://docs.oracle.com/javase/8/docs/api/javax/lang/model/SourceVersion.html#isName-java.lang.CharSequence-) without a dot at the end. Default: `"git"`.
dirtyValue                                                   | <a name="dirtyValue"/>Value for [`dirty`](#dirty) property. Default: `"dirty"`.
shortRevisionLength                                          | <a name="shortRevisionLength"/>Length of abbreviated SHA-1 for [`shortRevision`](#shortRevision) and [`shortParent`](#shortParent) properties, min. 0, max. 40. Default: 7.
gitDateFormat                                                | <a name="gitDateFormat"/>Format for Git [`authorDate`](#authorDate) and Git [`commitDate`](#commitDate) properties (see [SimpleDateFormat](https://docs.oracle.com/javase/8/docs/api/java/text/SimpleDateFormat.html)). The default locale will be used. TimeZone can be specified with [dateFormatTimeZone](#dateFormatTimeZone).<br>Default: `"yyyy-MM-dd"`.
buildDateFormat                                              | <a name="buildDateFormat"/>Format for [`buildDate`](#buildDate) property (see [SimpleDateFormat](https://docs.oracle.com/javase/8/docs/api/java/text/SimpleDateFormat.html)). The default locale will be used. TimeZone can be specified with [dateFormatTimeZone](#dateFormatTimeZone).<br>Default: `"yyyy-MM-dd HH:mm:ss"`.
dateFormatTimeZone                                           | <a name="dateFormatTimeZone"/>TimeZone for [gitDateFormat](#gitDateFormat) and [buildDateFormat](#buildDateFormat) parameters (see [TimeZone#getTimeZone(String)](https://docs.oracle.com/javase/8/docs/api/java/util/TimeZone.html#getTimeZone-java.lang.String-)).<br>Default: current default TimeZone, as returned by [TimeZone#getDefault()](https://docs.oracle.com/javase/8/docs/api/java/util/TimeZone.html#getDefault--). (Note that Maven's built-in `maven.build.timestamp` property cannot use the default time zone and always returns time in UTC.)
countCommits*InPath*                                         | <a name="countCommitsInPath"/>Relative path to a folder or a file in Git Repo. Only commits which affect the specified path will be counted. The path starts without the leading `/`; path to a folder may contain an optional trailing `/`.<br><br>The parameter is useful if you want to count commits only for a part of a Git repo. E.g. if your Git Repo contains application code under `app/` path and documentation under `docs/`, you can count commits separately (and have different buildNumbers) for each of those parts. See concrete example in [Ant](#usage-in-ant) section.<br>Default: not set (all commits get counted).<br><br>_Note: The commit specified with one of [countCommits**Since**](#countCommitsSince) parameters has to be among the commits remaining after applying the [countCommits**InPath**](#countCommitsInPath) parameter._
countCommits*SinceInclusive*<br>countCommits*SinceExclusive* | <a name="countCommitsSince"/>Specifies since which ancestor commit (inclusive or exclusive) to count commits. Can be specified as a tag (annotated or lightweight) or SHA-1 (complete or abbreviated).<br>If such commit is not found, error message is printed and build will fail (since otherwise you would get an unexpected wrong build number). If both, inclusive and exclusive parameters are specified, the "inclusive" version wins.<br><br>The parameter is useful if you only want to count commits since start of the current development iteration.<br>Default: not set (all commits get counted).<br><br>_Note: Technically, commits are counted backwards from HEAD to parents, through all branches which participated in HEAD state, from child to parent commit, in reverse chronological order of commits in parallel branches according to "committed date" of commits, until the specified ancestor commit is reached (or till root of Git repo). The traverse order should be exactly the same as displayed in "History" view of Eclipse._
buildNumberFormat                                            | <a name="buildNumberFormat"/>JavaScript expression to format/compose the [`buildNumber`](#buildNumber) property. Uses JS engine from JDK. All [extracted properties](#extracted-properties) are exposed to JavaScript as global String variables (names without "git" namespace). JavaScript engine is only initialized if `buildNumberFormat` is provided.<br><br>Example: `branch + "." + commitsCount + "/" + commitDate + "/" + shortRevision + (dirty.length > 0 ? "-" + dirty : "");`<br><br>Default: `<tag or branch>.<commitsCount>.<shortRevision>-<dirty>`<br> or, more precisely, equivalent of the following JavaScript (evaluation result of the last line gets returned; real implementation is in Java for performance):<br>`name = (tag.length > 0) ? tag : (branch.length > 0) ? branch : "UNNAMED";`<br>`name + "." + commitsCount + "." + shortRevision + (dirty.length > 0 ? "-" + dirty : "");`
repositoryDirectory                                          | <a name="repositoryDirectory"/>Directory to start searching Git root from, should contain `.git` directory or be a subdirectory of such directory. Default: project directory (Maven: `${project.basedir}`, Ant: `${basedir}`, Gradle: `projectDir`).
runOnlyAtExecutionRoot                                       | <a name="runOnlyAtExecutionRoot"/>Setting this parameter to `false` allows to re-read metadata from Git repo in every submodule of a Maven multi-module project, not only in the root one. Has no effect for Ant or Gradle. Default: `true`.
skip                                                         | <a name="skip"/>Setting this parameter to `true` will skip extraction of Git metadata and creation of buildNumber. Default: `false`.
verbose                                                      | <a name="verbose"/>Print more information during build (parameters, all extracted properties, changes that caused dirty status, number of tags, execution times, etc). Default: `false`.

Working with parameters is very similar in all build tools. See examples in sections for [Maven](#usage-in-maven), [Ant](#usage-in-ant), [Gradle](#usage-in-gradle).


Usage in Maven
--------------

Typical usage with writing extracted properties to the MANIFEST.MF file:

```xml
<build>
    <plugins>

        <plugin>
            <groupId>com.labun.buildnumber</groupId>
            <artifactId>jgit-buildnumber-maven-plugin</artifactId>
            <version>2.5.0</version>
            <executions>
                <execution>
                    <id>jgit-buildnumber</id>
                    <goals>
                        <goal>extract-buildnumber</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>

        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-jar-plugin</artifactId>
            <version>3.1.1</version>
            <configuration>
                <archive>
                    <manifestEntries>
                        <Version>${git.buildNumber}</Version>
                        <Build-Time>${git.buildDate}</Build-Time>
                    </manifestEntries>
                </archive>
            </configuration>
        </plugin>

    </plugins>
</build>
```

You can also write the extracted properties into arbitrary files (.properties, .java, ...) using [Maven resource filtering](https://maven.apache.org/plugins/maven-resources-plugin/examples/filter.html).

The plugin binds to the `validate` phase (the first Maven life cycle phase) per default, so that the extracted properties are available in all other phases. 

Configuration example:

```xml
<plugin>
    <groupId>com.labun.buildnumber</groupId>
    <artifactId>jgit-buildnumber-maven-plugin</artifactId>
    <version>2.5.0</version>
    <executions>
        <execution>
            <id>git-buildnumber</id>
            <goals>
                <goal>extract-buildnumber</goal>
            </goals>
            <configuration>
                <countCommitsSinceInclusive>v18-start</countCommitsSinceInclusive>
                <dirtyValue>DEV</dirtyValue>
                <buildNumberFormat>
                    branch + "." + commitsCount + "/" + commitDate + "/" + shortRevision + (dirty.length > 0 ? "-" + dirty : "");
                </buildNumberFormat>
                <verbose>true</verbose>
            </configuration>
        </execution>
    </executions>
</plugin>
```

If the plugin is defined in parent module of a __multi-module project__, it will access the Git repo only once. (If you want to change that, see [runOnlyAtExecutionRoot](#runOnlyAtExecutionRoot).) The properties extracted in parent module are propagated to all child modules. This only applies to normal Maven builds though, not for Eclipse m2e incremental builds, since Eclipse / OSGI has a flat workspace and doesn't support nested Maven modules.

The plugin contains lifecycle-mapping-metadata for __Eclipse m2e__, and will be executed in m2e incremental builds (yet not on configuration). 
This is particularly important for local deployments to a JEE server from within Eclipse, if you want to see the proper build number in your web application. (Local deployment somehow depends on m2e incremental build).

> If you observe performance problems, "Run on incremental" can be disabled by adding the following to Eclipse m2e workspace `lifecycle-mapping-metadata.xml` (Eclipse > Window > Preferences > Maven > Lifecycle Mappings):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<lifecycleMappingMetadata>
    <pluginExecutions>
        <pluginExecution>
            <pluginExecutionFilter>
                <groupId>com.labun.buildnumber</groupId>
                <artifactId>jgit-buildnumber-maven-plugin</artifactId>
                <versionRange>[0.0,)</versionRange>
                <goals>
                    <goal>extract-buildnumber</goal>
                </goals>
            </pluginExecutionFilter>
            <action>
                <!-- disables plugin for m2e incremental builds -->
                <ignore/>
            </action>
        </pluginExecution>
    </pluginExecutions>
</lifecycleMappingMetadata>
```
> Restart Eclipse thereafter ("apply" in Preferences is not enough).


Usage in Ant
------------

Usage is very similar to Maven. As all parameters are optional you don't have to specify any. Excerpt from `build.xml`:

```xml
<target name="jgit-buildnumber">
    <taskdef name="extract-buildnumber" classname="com.labun.buildnumber.JGitBuildNumberAntTask" classpathref="dependencies" />
    <extract-buildnumber />
</target>
```

See [complete `build.xml` example with task parameters](examples/ant/build.xml).

Another example shows how to [extract two different buildNumbers for two repositories](examples/ant/build-with-2-targets.xml) (e.g. "application" and "documentation") in one build file. 


Usage in Gradle
----------------

Usage is very similar to Maven and Ant. Essentially, you only need to specify the dependency on `jgit-buildnumber-gradle-plugin`.

Complete working example of `build.gradle`:

```gradle
buildscript {
    repositories { mavenLocal(); mavenCentral() }
    dependencies { classpath 'com.labun.buildnumber:jgit-buildnumber-gradle-plugin:2.5.0' }
}

import com.labun.buildnumber.JGitBuildNumberGradleTask

task 'extract-buildnumber' (type: JGitBuildNumberGradleTask)
```

See [extended `build.gradle` example with task parameters](examples/gradle/build.gradle).

The only difference in setting task parameters with Gradle 
(as compared to [Maven](https://maven.apache.org/guides/plugin/guide-java-plugin-development.html#Parameters) 
and [Ant](https://ant.apache.org/manual/develop.html#set-magic)) is that Gradle doesn't implicitly convert strings to other types.
Therefore you have to do it explicitly. "JGit Build Number" has only one such parameter: `repositoryDirectory` of type `java.io.File`.
If you need to specify this parameter, simply call an appropriate constructor:

```gradle
task 'extract-buildnumber' (type: JGitBuildNumberGradleTask) {
    repositoryDirectory = new File('<absolute path>') // or: repositoryDirectory = file('<relative path>')
    ...
}
```

Development notes
-----------------

This section is intended for developers of "JGit Build Number". It can be ignored if you only _use_ the plugins in your projects.

The project uses [Lombok](https://projectlombok.org/), a great tool which helps to free the source code from ugly boilerplate like getters and setters.
Lombok is supported by all major IDEs and build tools. For Eclipse, simply add this to `eclipse.ini`:

    -vmargs
    -javaagent:<path-to-lombok-jar>

For other IDEs and tools, see [projectlombok.org/setup](https://projectlombok.org/setup/).


License information
-------------------

This project is released under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).


Changelog
---------

#### 2.5.0 (not released yet)
- dependency updates: jgit 5.12.0.202106070339-r, lombok 1.18.20, maven-plugin-api 3.8.1, maven-core 3.8.1, maven-plugin-annotations 3.6.1, ant 1.10.10, groovy 2.5.14
- if git status is dirty, log which changes caused that (verbose mode only)
- use [standalone version of Nashorn JavaScript engine](https://github.com/openjdk/nashorn) if running on Java 11+<br>
  (prevents deprecation warning on Java 11-14; enables working with Java 15+ where Nashorn is not a part of JDK anymore)
- initialize JavaScript engine in parallel with reading Git repo (reduces overall execution time by ca. 0.5 s)
- log number of tags (verbose mode only)
- print some error resolution hints if [countCommitsSince...](#countCommitsSince) in conjunction with [countCommitsInPath](#countCommitsInPath) was used and no such commit (from the `countCommitsSince` parameter) is found
- homepage URL added to poms: https://github.com/elab/jgit-buildnumber
- new property: `git.buildDateMillis`

#### 2.4.0 (2020-01-01)
- new property: `git.nearestTag`

#### 2.3.1 (2019-10-01)
- `git.describe`: assuring not null result to prevent plugin fail (thanks to [RobertPaasche](https://github.com/RobertPaasche))
- `git.describe`: also consider lightweight (not annotated) tags
