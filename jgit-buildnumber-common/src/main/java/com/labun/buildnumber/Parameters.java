package com.labun.buildnumber;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

import javax.lang.model.SourceVersion;

public interface Parameters {

    //@formatter:off
    /** Properties are published with this namespace prefix. You may want to redefine the default value:<ul>
     * <li>to avoid name clashes with other plugins/tasks;
     * <li>to extract properties for multiple Git repos (use multiple plugin/task executions with different namespaces for that). 
     * </ul>
     * Default: "git". */
    String getNamespace();

    /** Value for `dirty` property. Default: "dirty". */
    String getDirtyValue();

    /** Length of abbreviated SHA-1 for `shortRevision` and `shortParent` properties, min. 0, max. 40. Default: 7. */
    Integer getShortRevisionLength();

    /** Format for Git `authorDate` and Git `commitDate` properties (see Java {@link SimpleDateFormat}). The default locale will be used. TimeZone can be specified with `dateFormatTimeZone`. Default: "yyyy-MM-dd". */
    String getGitDateFormat();

    /** Format for `buildDate` property (see Java {@link SimpleDateFormat}). The default locale will be used. TimeZone can be specified with `dateFormatTimeZone`. Default: "yyyy-MM-dd HH:mm:ss". */
    String getBuildDateFormat();

    /** TimeZone for `gitDateFormat` and `buildDateFormat` parameters (see Java {@link TimeZone#getTimeZone(String)}). Default: current default TimeZone, as returned by {@link TimeZone#getDefault()}. 
     *  (Note that Maven's built-in `maven.build.timestamp` property cannot use the default time zone and always return time in UTC.) */
    String getDateFormatTimeZone();
    
    /** Specifies since which ancestor commit (inclusive or exclusive) to count commits. 
     * Can be specified as a tag (annotated or lightweight) or SHA-1 (complete or abbreviated).<br>
     * If such commit is not found, error message is printed in log and all commits are counted. 
     * If both, inclusive and exclusive parameters are specified, the "inclusive" version wins.
     * <p>
     * The parameter is useful if you only want to count commits since start of the current development iteration.<br>
     * Default: not set (all commits get counted).
     * <p><i>Note: Technically, commits are counted backwards from HEAD to parents, through all branches which participated in HEAD state, 
     * from child to parent commit, in reverse chronological order of commits in parallel branches according to "committed date" of commits, 
     * until the specified ancestor commit is reached (or till root of Git repo). 
     * The traverse order should be exactly the same as displayed in "History" view of Eclipse.</i> */
    String getCountCommitsSinceInclusive();

    /** See {@link #getCountCommitsSinceInclusive()} */
    String getCountCommitsSinceExclusive();

    /** JavaScript expression to format/compose the `buildNumber` property. Uses JS engine from JDK. 
     * All extracted properties are exposed to JavaScript as global String variables (names without "git" namespace). 
     * JavaScript engine is only initialized if `buildNumberFormat` is provided.
     * <p>
     * Example:
     * <pre>branch + "." + commitsCount + "/" + commitDate + "/" + shortRevision + (dirty.length > 0 ? "-" + dirty : "");</pre> 
     * <p>
     * Default: <pre>&lt;tag or branch>.&lt;commitsCount>.&lt;shortRevision>-&lt;dirty></pre>
     * or , more precisely, equivalent of the following JavaScript (evaluation result of the last line gets returned; 
     * real implementation is in Java for performance):
     * <pre>
     * name = (tag.length > 0) ? tag : (branch.length > 0) ? branch : "UNNAMED";
     * name + "." + commitsCount + "." + shortRevision + (dirty.length > 0 ? "-" + dirty : "");
     * </pre>
     * */
    String getBuildNumberFormat();

    /** Directory to start searching Git root from, should contain `.git` directory
     *  or be a subdirectory of such directory. Default: project directory (Maven: `${project.basedir}`, Ant: `${basedir}`, Gradle: `projectDir`). */
    File getRepositoryDirectory();

    /** Setting this parameter to `false` allows to re-read metadata from Git repo in every submodule of a Maven multi-module project, 
     * not only in the root one. Has no effect for Ant or Gradle. Default: `true`. */
    Boolean getRunOnlyAtExecutionRoot();

    /** Setting this parameter to 'true' will skip extraction of Git metadata and creation of buildNumber. Default: `false`. */
    Boolean getSkip();

    /** Print more information during build (e.g. parameters, all extracted properties, execution times). Default: `false`. */
    Boolean getVerbose();

    void setNamespace(String param);
    void setDirtyValue(String param);
    void setShortRevisionLength(Integer param);
    void setGitDateFormat(String param);
    void setBuildDateFormat(String param);
    void setDateFormatTimeZone(String param);
    void setCountCommitsSinceInclusive(String param);
    void setCountCommitsSinceExclusive(String param);
    void setBuildNumberFormat(String param);
    void setRepositoryDirectory(File param);
    void setRunOnlyAtExecutionRoot(Boolean param);
    void setSkip(Boolean param);
    void setVerbose(Boolean param);
    //@formatter:on

    /** Validates user parameters and sets omitted parameters to default values (where required).
     *  <p> 
     *  Parameters should be accessed with care until this method is called. */
    default void validateAndSetParameterValues() {
        if (getNamespace() == null || !SourceVersion.isName(getNamespace())) setNamespace("git");
        if (getDirtyValue() == null) setDirtyValue("dirty");
        if (getShortRevisionLength() == null || getShortRevisionLength() < 0 || getShortRevisionLength() > 40) setShortRevisionLength(7);
        if (getGitDateFormat() == null) setGitDateFormat("yyyy-MM-dd");
        if (getBuildDateFormat() == null) setBuildDateFormat("yyyy-MM-dd HH:mm:ss");
        if (getRepositoryDirectory() == null) setRepositoryDirectory(new File("."));
        if (getRunOnlyAtExecutionRoot() == null) setRunOnlyAtExecutionRoot(true);
        if (getSkip() == null) setSkip(false);
        if (getVerbose() == null) setVerbose(false);
    }

    default String asString() {
        return "namespace=" + getNamespace() + ", dirtyValue=" + getDirtyValue() + ", shortRevisionLength=" + getShortRevisionLength() + ", gitDateFormat="
            + getGitDateFormat() + ", buildDateFormat=" + getBuildDateFormat() + ", dateFormatTimeZone=" + getDateFormatTimeZone()
            + ", countCommitsSinceInclusive=" + getCountCommitsSinceInclusive() + ", countCommitsSinceExclusive=" + getCountCommitsSinceExclusive() + ", "
            + "buildNumberFormat=" + getBuildNumberFormat() + ", repositoryDirectory=" + getRepositoryDirectory() + ", runOnlyAtExecutionRoot="
            + getRunOnlyAtExecutionRoot() + ", skip=" + getSkip() + ", verbose=" + getVerbose();
    }
}
