JGit Build Number for Maven, Ant, and Gradle
============================================

Extracts Git metadata and a freely composable build number in pure Java without Git command-line tool. Eclipse m2e compatible.

Based on the [work of Alex Kasko](https://github.com/alx3apps/jgit-buildnumber) with [merged changes from other forks](https://github.com/elab/jgit-buildnumber/network). Thank you, [guys](https://github.com/elab/jgit-buildnumber/graphs/contributors), for contribution! Additionally, contains bug fixes, new features, and performance improvements. The code has been almost completely rewritten.

Available from [Maven central](http://repo1.maven.org/maven2/com/labun/buildnumber/).


Build Number
------------

Build number should identify the code state of the project, from which it has been created. 
Particularly, it should *not* depend on: 
- where the build takes place (locally, build server);
- how many times the same project state has been build (i.e. no simple increment).

In our case the __default BuildNumber__ looks like `v19.3351.ddda02b` and consists of:

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

- _dirty flag_: if differences exist between working-tree, index, and HEAD; you cannot trust the build in this case :)<br>
  the whole BuildNumber would look like `v19.3351.ddda02b-dirty`;
  ```
  git status
  ```

Instead of the Git CLI commands above, the pure Java [JGit](http://www.jgit.org/) API is used.
Note: The JGit output (intentionally) doesn't coincide exactly with the output of Git CLI. 
E.g. branch and tag names are returned without the `refs/...` prefix.

**The default BuildNumber can be easily redefined using extracted properties (see [buildNumberFormat](#buildNumberFormat)).**
 
All properties, including BuildNumber, are available in Maven, Ant, or Gradle build for the entire application.


Usage in Maven 3
----------------

Typical usage with writing extracted properties to the MANIFEST.MF file:

```xml
<build>
    <plugins>

        <plugin>
            <groupId>com.labun.buildnumber</groupId>
            <artifactId>jgit-buildnumber-maven-plugin</artifactId>
            <version>2.0.0</version>
            <executions>
                <execution>
                    <id>git-buildnumber</id>
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

The plugin binds per default to the `validate` phase, the first Maven life cycle phase, so that the extracted properties are available in all other phases. 

In the build log (or in Eclipse Maven console) you can see all the extracted properties and execution time.

If the plugin is defined in parent module of a __multi-module project__, it will access the Git repo only once. (If you will change that, see [runOnlyAtExecutionRoot](#runOnlyAtExecutionRoot).) The properties extracted in parent module get propagated to all child modules. It works this way only for normal Maven builds though, not for Eclipse m2e incremental builds, since Eclipse / OSGI has flat workspace and doesn't support nested Maven modules.

The plugin contains lifecycle-mapping-metadata for __Eclipse m2e__, and will be executed in m2e incremental builds (yet not on configuration). 
This is particularly important for local deployments to a JEE server from within Eclipse, if you want to see the proper build number in your web application. (Local deployment somehow depends on m2e incremental build).

> Only if you issuing performance problems due to continuous plugin execution by Eclipse m2e incremental build, "Run on incremental" can be disabled by adding the following to Eclipse m2e workspace `lifecycle-mapping-metadata.xml` (Eclipse > Window > Preferences > Maven > Lifecycle Mappings):

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

__Execution time__ depends first of all on the complexity of Git repo (especially on the number of tags, followed by the number of commits) and whether you use a custom JS `buildNumberFormat` or not. Without custom `buildNumberFormat`, you should expect regular Maven execution time of 0.5 - 1.5 s. Add 0.5 s. if custom `buildNumberFormat` is used. Eclipse m2e incremental execution is much faster (often factor of 2) than the regular Maven execution. 64 bit JRE is significantly faster than 32 bit JRE, warm is faster than cold, and so on.


### Extracted properties

property          | desc
------------------|----------------
git.revision      | HEAD SHA-1
git.shortRevision | HEAD SHA-1 abbreviated (7 chars)
git.dirty         | [dirtyValue](#dirtyValue) if differences exist between working-tree, index, and HEAD; empty string otherwise
git.branch        | branch name; empty string for detached HEAD
git.tag           | tag name; empty string if no tags defined; multiple tags separated with `;`
git.parent        | SHA-1 of the parent commit; multiple parents separated with `;`
git.commitsCount  | commits count; computed by traversing the history from HEAD backwards; returns -1 for a Git shallow clone
git.authorDate    | when HEAD commit has been authored
git.commitDate    | when HEAD commit has been committed
git.describe      | result of Git `describe` command
git.buildDate     | when build has started
git.buildNumber   | composed from other properties according to [buildNumberFormat](#buildNumberFormat) parameter 


### Configuration

All parameters are optional. 
Configuration goes under `<configuration>` tag under `<execution>` section.

param                                                        | desc
-------------------------------------------------------------|----------------------------------------------
<a name="dirtyValue">dirtyValue</a>                          | Value for `git.dirty` flag; default: String `dirty`.
gitDateFormat                                                | Format for `git.authorDate` and `git.commitDate` (see Java `SimpleDateFormat`). The default locale will be used. TimeZone can be specified with `dateFormatTimeZone`.<br>Default: `yyyy-MM-dd`.
buildDateFormat                                              | Format for `git.buildDate` (see Java `SimpleDateFormat`). The default locale will be used. TimeZone can be specified with `dateFormatTimeZone`.<br>Default: `yyyy-MM-dd HH:mm:ss`.
dateFormatTimeZone                                           | TimeZone for `gitDateFormat` and `buildDateFormat`. For possible values see Java `TimeZone#getTimeZone(String)`.<br>Default: current default TimeZone, as returned by Java `TimeZone#getDefault()`.
countCommitsSince*Inclusive*<br>countCommitsSince*Exclusive* | Specifies since which ancestor commit (inclusive or exclusive) to count commits. Can be specified as tag (annotated or lightweight) or SHA-1 (complete or abbreviated).<br>If such commit is not found, all commits get counted. If both, inclusive and exclusive parameters are specified, the "inclusive" version wins.<br><br>Useful if you only want to count commits since start of the current development iteration.<br>Default: not set (all commits get counted). 
<a name="buildNumberFormat">buildNumberFormat</a>            | JavaScript expression to format/compose the `git.buildNumber`. Uses JS engine from JDK. All properties are exposed to JavaScript as global String variables (names without `git.` prefix). JavaScript engine is initialized only if `buildNumberFormat` is provided.<br><br>Example: `branch + "." + commitsCount + "/" + commitDate + "/" + shortRevision + (dirty.length > 0 ? "-" + dirty : "");`<br><br>Default: `<tag or branch>.<commitsCount>.<shortRevision>-<dirty>`<br> or, more precisely, equivalent of JavaScript (evaluation result of the last line gets returned; real implementation is in Java for performance):<br>`name = (tag.length > 0) ? tag : (branch.length > 0) ? branch : "UNNAMED";`<br>`name + "." + commitsCount + "." + shortRevision + (dirty.length > 0 ? "-" + dirty : "");`
repositoryDirectory                                          | Directory to start searching Git root from, should contain `.git` directory or be a subdirectory of such directory. Default: `${project.basedir}`.
<a name="runOnlyAtExecutionRoot">runOnlyAtExecutionRoot</a>  | Setting this parameter to `false` allows to re-read metadata from Git repo in every submodule of a multi-module project, not only in the root one. Default: `true`.
skip                                                         | Setting this parameter to `true` will skip plugin execution. Default: `false`.


Configuration example:

    <plugin>
        <groupId>com.labun.buildnumber</groupId>
        <artifactId>jgit-buildnumber-maven-plugin</artifactId>
        <version>2.0.0</version>
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
                </configuration>
            </execution>
        </executions>
    </plugin>


Usage in Ant
------------

To use JGit BuildNumber Ant task you need these jars on your classpath:

 - `jgit-buildnumber-ant-task-2.0.0.jar`
 - `org.eclipse.jgit-5.2.1.201812262042-r.jar`

Project directory that contains `.git` directory may be provided with `git.repositoryDirectory` property.
Current work directory is used by default.

Extracted properties are the same as with Maven.

build.xml usage snippet:

    <target name="git-revision">
        <taskdef name="jgit-buildnumber" classname="com.labun.buildnumber.JGitBuildNumberAntTask" classpathref="lib.static.classpath"/>
        <jgit-buildnumber/>
        <echo>Git version extracted ${git.commitsCount} (${git.shortRevision})</echo>
    </target>

If you want to customize the default `git.buildNumber`, you can use Ant [Script task](http://ant.apache.org/manual/Tasks/script.html) like this:

    <target name="git-revision">
        <taskdef name="jgit-buildnumber" classname="com.labun.buildnumber.JGitBuildNumberAntTask" classpathref="lib.static.classpath"/>
        <jgit-buildnumber/>
        <script language="javascript">
            var tag = project.getProperty("git.tag")
            var revision = project.getProperty("git.shortRevision")
            var buildNumber = tag + "_" + revision
            project.setProperty("git.buildNumber", buildNumber)
        </script>
    </target>


Usage in Gradle
----------------

 - Add the plugin dependency in your build.gradle: `classpath 'com.labun.buildnumber:jgit-buildnumber-gradle-plugin:2.0.0'`
 - Apply the plugin in one of the following ways: `apply plugin: 'jgit-buildnumber-gradle-plugin'` or `apply plugin: com.labun.buildnumber.JGitBuildNumberGradlePlugin`
 - Execute the jGitBuildNumber_ExtractBuildNumber task: `tasks.jGitBuildNumber_ExtractBuildNumber.execute()`

Extracted properties are put into: 

 - `project.gitRevision`
 - `project.gitShortRevision`
 - `project.gitDirty`
 - `project.gitBranch`
 - `project.gitTag`
 - `project.gitParent`
 - `project.gitCommitsCount`
 - `project.gitAuthorDate`
 - `project.gitCommitDate`
 - `project.gitDescribe`
 - `project.gitBuildDate`
 - `project.gitBuildNumber`


Example setup in build.gradle:

    buildscript {
      repositories{  
         mavenLocal()  
      }  
      dependencies {  
         classpath 'com.labun.buildnumber:jgit-buildnumber-gradle-plugin:2.0.0'  
      }  
    }  
    apply plugin: 'jgit-buildnumber-gradle-plugin'  

The default working directory in the plugin is ".", i.e current directory. 
If you wish to set a custom directory then the following should be added to your build.gradle (`projectDir` is just an example):

    task jGitBuildNumber_ExtractBuildNumber() {
       dir = projectDir;
    }

Usage example (write extracted properties into MANIFEST.MF file):

    jar() {
        manifest {
            attributes(
                    'Main-Class': mainClassName,
                    'Implementation-Title': project.name,
                    'Implementation-Version': project.gitRevision,
                    'Specification-Version': project.gitCommitsCount,
                    'X-Git-Branch': project.gitBranch,
                    'X-Git-Tag': project.gitTag,
                    'Version' : project.gitCommitsCount,
                    'Branch' : project.gitBranch
            )
        }
    }  

Result example (from MANIFEST.MF):

    Manifest-Version: 1.0
    Main-Class: SearchService.application.SearchServiceMain
    Implementation-Title: SearchService
    Implementation-Vendor: 
    Implementation-Version: 3b03c4d3531c66648c4fe7fcd7f53b3e9f64e519
    Specification-Version: 82
    X-Git-Branch: master
    X-Git-Tag: 
    Version: 82
    Branch: master


License information
-------------------

This project is released under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)

