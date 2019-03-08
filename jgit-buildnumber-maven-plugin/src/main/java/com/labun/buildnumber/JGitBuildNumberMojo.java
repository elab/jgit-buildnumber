package com.labun.buildnumber;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.TimeZone;
import java.util.TreeMap;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.sonatype.plexus.build.incremental.BuildContext;

/** Goal which extracts Git metadata and creates build number. */
@Mojo(name = "extract-buildnumber", defaultPhase = LifecyclePhase.VALIDATE)
public class JGitBuildNumberMojo extends AbstractMojo {

    @Component
    private BuildContext buildContext;

    // ---------- extracted properties ----------

    @Parameter(readonly = true)
    private String revisionProperty = "git.revision";

    @Parameter(readonly = true)
    private String shortRevisionProperty = "git.shortRevision";

    /** {@link #dirtyValue} if differences exist between working-tree, index, and HEAD; empty string otherwise. */
    @Parameter(readonly = true)
    private String dirtyProperty = "git.dirty";
    
    @Parameter(readonly = true)
    private String branchProperty = "git.branch";

    @Parameter(readonly = true)
    private String tagProperty = "git.tag";

    @Parameter(readonly = true)
    private String parentProperty = "git.parent";

    @Parameter(readonly = true)
    private String commitsCountProperty = "git.commitsCount";

    @Parameter(readonly = true)
    private String authorDateProperty = "git.authorDate";

    @Parameter(readonly = true)
    private String commitDateProperty = "git.commitDate";

    @Parameter(readonly = true)
    private String describeProperty = "git.describe";

    @Parameter(readonly = true)
    private String buildDateProperty = "git.buildDate";

    /** Default value is equivalent to the JavaScript:
     * <pre>
     * name = (tag.length > 0) ? tag : (branch.length > 0) ? branch : "UNNAMED";
     * name + "." + commitsCount + "." + shortRevision + (dirty.length > 0 ? "-" + dirty : "");
     * </pre>
     * It can be overwritten using {@link #buildNumberFormat}.
     *  */
    @Parameter(readonly = true)
    private String buildNumberProperty = "git.buildNumber";

    // ---------- parameters (user configurable) ----------

    /** Which string to use for `dirty` property. */
    @Parameter(defaultValue = "dirty")
    private String dirtyValue;

    /** Which format to use for Git authorDate and Git commitDate. The default locale will be used. TimeZone can be specified, see {@link #dateFormatTimeZone}. */
    @Parameter(defaultValue = "yyyy-MM-dd")
    private String gitDateFormat;

    /** Which format to use for buildDate.  The default locale will be used. TimeZone can be specified, see {@link #dateFormatTimeZone}. */
    @Parameter(defaultValue = "yyyy-MM-dd HH:mm:ss")
    private String buildDateFormat;

    /** TimeZone for {@link #gitDateFormat} and {@link #buildDateFormat}. Default: current default TimeZone, as returned by {@link TimeZone#getDefault()}. 
     * For possible values see {@link TimeZone#getTimeZone(String)}. */
    @Parameter
    private String dateFormatTimeZone;

    /** Since which ancestor commit (inclusive) to count commits. Can be specified as tag (annotated or lightweight) or SHA-1 (complete or abbreviated).
     *  If such commit is not found, all commits get counted. 
     *  See also {@link #countCommitsSinceExclusive}. If both, inclusive and exclusive "countCommitsSince" parameters are specified, the {@link #countCommitsSinceInclusive} wins. */
    @Parameter
    private String countCommitsSinceInclusive;

    /** Since which ancestor commit (exclusive) to count commits. Can be specified as tag (annotated or lightweight) or SHA-1 (complete or abbreviated).
     *  If such commit is not found, all commits get counted.  
     *  See also {@link #countCommitsSinceInclusive}. If both, inclusive and exclusive "countCommitsSince" parameters are specified, the {@link #countCommitsSinceInclusive} wins. */
    @Parameter
    private String countCommitsSinceExclusive;

    /** JavaScript expression to format/compose the buildnumber. All properties can be used (without prefix), e.g. 
     * <pre>branch + "." + commitsCount + "/" + commitDate + "/" + shortRevision + (dirty.length > 0 ? "-" + dirty : "");</pre>
     * See also {@link #buildNumberProperty}. */
    @Parameter
    private String buildNumberFormat;

    /** Directory to start searching Git root from, should contain '.git' directory
     *  or be a subdirectory of such directory. '${project.basedir}' is used by default. */
    @Parameter(defaultValue = "${project.basedir}")
    private File repositoryDirectory;

    /** Setting this parameter to 'false' allows to execute plugin in every submodule, not only in root one. */
    @Parameter(defaultValue = "true")
    private boolean runOnlyAtExecutionRoot;

    /** Setting this parameter to 'true' will skip plugin execution. */
    @Parameter(defaultValue = "false")
    private boolean skip;

    // ---------- parameters (read only) ----------

    @Parameter(property = "project.basedir", readonly = true, required = true)
    private File baseDirectory;

    @Parameter(property = "session.executionRootDirectory", readonly = true, required = true)
    private File executionRootDirectory;

    /** The maven project. */
    @Parameter(property = "project", readonly = true)
    private MavenProject project;

     /** The maven parent project. */
    @Parameter(property = "project.parent", readonly = true)
    private MavenProject parentProject;

    // ---------- implementation ----------

    /** Extracts buildnumber fields from git repository and publishes them as maven properties.
     *  Executes only once per build. Return default (unknown) buildnumber fields on error. */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info("JGit BuildNumber Maven Plugin - start");
        long start = System.currentTimeMillis();

        executeImpl();

        long duration = System.currentTimeMillis() - start;
        getLog().info(String.format("JGit BuildNumber Maven Plugin - end (execution time: %d ms)", duration));
    }

    public void executeImpl() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Execution of plugin is skipped by configuration.");
            return;
        }

        getLog().info("executionRootDirectory: " + executionRootDirectory + ", runOnlyAtExecutionRoot: " + runOnlyAtExecutionRoot + ", baseDirectory: " + baseDirectory + ", repositoryDirectory: " + repositoryDirectory);

        try {

            // executes only once per build
            // http://www.sonatype.com/people/2009/05/how-to-make-a-plugin-run-once-during-a-build/
            if (!runOnlyAtExecutionRoot || executionRootDirectory.equals(baseDirectory)) {

                long t = System.currentTimeMillis();
                BuildNumberExtractor extractor = new BuildNumberExtractor(repositoryDirectory);
                getLog().info("initializing Git repo, get base data: " + (System.currentTimeMillis() - t) + " ms");

                String headSha1 = extractor.getHeadSha1Short();
                String dirty = extractor.isGitStatusDirty() ? dirtyValue : null;

                List<String> params = Arrays.asList(headSha1, dirty, gitDateFormat, buildDateFormat, dateFormatTimeZone, countCommitsSinceInclusive, countCommitsSinceExclusive,  buildNumberFormat);
                getLog().info("params: " + params);
                String paramsKey = "jgitParams";
                String resultKey = "jgitResult";

                // note: saving/loading custom classes doesn't work (due to different classloaders?, "cannot be cast" error);
                // when saving Properties object, or values don't survive; 
                // therefore we use a Map here
                Map<String, String> result = getCachedResultFromBuildConext(paramsKey, params, resultKey);
                if (result != null) {
                    getLog().info("using cached result");
                } else {
                    t = System.currentTimeMillis();
                    result = extractor.extract(gitDateFormat, buildDateFormat, dateFormatTimeZone, countCommitsSinceInclusive, countCommitsSinceExclusive, dirtyValue);
                    getLog().info("extracting properties for buildnumber: " + (System.currentTimeMillis() - t) + " ms");

                    if (buildNumberFormat != null) {
                        t = System.currentTimeMillis();
                        String jsBuildNumber = formatBuildNumberWithJS(result);
                        // overwrite the default buildNumber
                        String defaultBuildNumber = result.put("buildNumber", jsBuildNumber);
                        getLog().info("overwriting default buildNumber: " + defaultBuildNumber);
                        getLog().info("formatting buildNumber with JS: " + (System.currentTimeMillis() - t) + " ms");
                    }
                }

                getLog().info("BUILDNUMBER: " + result.get("buildNumber"));
                getLog().info("all extracted properties: " + result);
                setProperties(result, project.getProperties());
                saveResultToBuildContext(paramsKey, params, resultKey, result);

            } else if("pom".equals(parentProject.getPackaging())) {
                // build started from parent, we are in subproject, lets provide parent properties to our project
                Properties parentProps = parentProject.getProperties();
                String revision = parentProps.getProperty(revisionProperty);
                if(null == revision) {
                    // we are in subproject, but parent project wasn't build this time,
                    // maybe build is running from parent with custom module list - 'pl' argument
                    getLog().warn("Cannot extract Git info, maybe custom build with 'pl' argument is running");
                    fillPropsUnknown();
                    return;
                }
                getLog().info("using already extracted properties from parent module: " + toMap(parentProps));
                copyProperties(parentProps, project.getProperties());

            } else {
                // should not happen
                getLog().warn("Cannot extract JGit version: something wrong with build process, we're not in parent, not in subproject!");
                fillPropsUnknown();
            }
        } catch (Exception e) {
            getLog().error(e);
            fillPropsUnknown();
        }
    }

    // m2e build? => save extracted values to BuildContext
    private void saveResultToBuildContext(String paramsKey, List<String> currentParams, String resultKey, Map<String, String> result) {
        if (buildContext != null) {
            buildContext.setValue(paramsKey, currentParams);
            buildContext.setValue(resultKey, result);
        }
    }

    // m2e incremental build and input params (HEAD, etc.) not changed? => try to get previously extracted values from BuildContext
    // note: buildContext != null only in m2e builds in Eclipse
    private Map<String, String> getCachedResultFromBuildConext(String paramsKey, List<String> currentParams, String resultKey) {
        if (buildContext != null && buildContext.isIncremental()) {
            getLog().info("m2e incremental build detected");
            // getLog().info("buildContext.getClass(): " + buildContext.getClass()); // org.eclipse.m2e.core.internal.embedder.EclipseBuildContext
            List<String> cachedParams = (List<String>) buildContext.getValue(paramsKey);
            // getLog().info("cachedParams: " + cachedParams);
            if (Objects.equals(cachedParams,  currentParams)) {
                Map<String,String> cachedResult = (Map<String,String>) buildContext.getValue(resultKey);
                // getLog().info("cachedResult: " + cachedResult);
                return cachedResult;
            }
        }
        return null;
    }

    private Map<String, String> toMap(Properties props) {
        Map<String, String> map = new TreeMap<>();

        map.put(revisionProperty, props.getProperty(revisionProperty));
        map.put(shortRevisionProperty, props.getProperty(shortRevisionProperty));
        map.put(dirtyProperty, props.getProperty(dirtyProperty));
        map.put(branchProperty, props.getProperty(branchProperty));
        map.put(tagProperty, props.getProperty(tagProperty));
        map.put(parentProperty, props.getProperty(parentProperty));
        map.put(commitsCountProperty, props.getProperty(commitsCountProperty));
        map.put(authorDateProperty, props.getProperty(authorDateProperty));
        map.put(commitDateProperty, props.getProperty(commitDateProperty));
        map.put(describeProperty, props.getProperty(describeProperty));
        map.put(buildDateProperty, props.getProperty(buildDateProperty));
        map.put(buildNumberProperty, props.getProperty(buildNumberProperty));
        return map;
    }

    private void setProperties(Map<String,String> source, Properties target) {
        for (Map.Entry<String,String> e : source.entrySet()) target.setProperty("git." + e.getKey(), e.getValue());
    }

    private void copyProperties(Properties source, Properties target) {
        target.setProperty(revisionProperty, source.getProperty(revisionProperty));
        target.setProperty(shortRevisionProperty, source.getProperty(shortRevisionProperty));
        target.setProperty(dirtyProperty, source.getProperty(dirtyProperty));
        target.setProperty(branchProperty, source.getProperty(branchProperty));
        target.setProperty(tagProperty, source.getProperty(tagProperty));
        target.setProperty(parentProperty, source.getProperty(parentProperty));
        target.setProperty(commitsCountProperty, source.getProperty(commitsCountProperty));
        target.setProperty(authorDateProperty, source.getProperty(authorDateProperty));
        target.setProperty(commitDateProperty, source.getProperty(commitDateProperty));
        target.setProperty(describeProperty, source.getProperty(describeProperty));
        target.setProperty(buildDateProperty, source.getProperty(buildDateProperty));
        target.setProperty(buildNumberProperty, source.getProperty(buildNumberProperty));

    }

    private void fillPropsUnknown() {
        Properties props = project.getProperties();
        props.setProperty(revisionProperty, "UNKNOWN_REVISION");
        props.setProperty(shortRevisionProperty, "UNKNOWN_SHORT_REVISION");
        props.setProperty(dirtyProperty, "UNKNOWN_DIRTY");
        props.setProperty(branchProperty, "UNKNOWN_BRANCH");
        props.setProperty(tagProperty, "UNKNOWN_TAG");
        props.setProperty(parentProperty, "UNKNOWN_PARENT");
        props.setProperty(commitsCountProperty, "UNKNOWN_COMMITS_COUNT");
        props.setProperty(authorDateProperty, "UNKNOWN_AUTHOR_DATE");
        props.setProperty(commitDateProperty, "UNKNOWN_COMMIT_DATE");
        props.setProperty(describeProperty, "UNKNOWN_DESCRIBE");
        props.setProperty(buildDateProperty, "UNKNOWN_BUILD_DATE");
        props.setProperty(buildNumberProperty, "UNKNOWN_BUILDNUMBER");
    }

    private String formatBuildNumberWithJS(Map<String, String> bn) throws ScriptException {
        String engineName = "JavaScript";
        // find JavaScript engine using context class loader
        ScriptEngine jsEngine = new ScriptEngineManager().getEngineByName(engineName);
        if (jsEngine == null) {
            // may be null when running within Eclipse using m2e, maybe due to OSGi class loader;
            // this does work in Eclipse, see ScriptEngineManager constructor Javadoc for what passing a null means here
            jsEngine = new ScriptEngineManager(null).getEngineByName(engineName);
        }
        if (jsEngine == null) {
            getLog().error(engineName + " not found");
            return "UNKNOWN_JS_BUILDNUMBER";
        }

        for (Map.Entry<String,String> e : bn.entrySet()) jsEngine.put(e.getKey(), e.getValue());
        Object res = jsEngine.eval(buildNumberFormat);
        if (res == null) throw new IllegalStateException("JS buildNumber is null");
        return res.toString();
    }
}
