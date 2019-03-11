package com.labun.buildnumber;

import static com.labun.buildnumber.BuildNumberExtractor.propertyNames;

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

    // ---------- parameters (user configurable) ----------

    /** Properties are published with this "namespace" prefix. You may want to redefine the default value:<ul>
     * <li>to avoid name clashes with other plugins;
     * <li>to extract properties for multiple Git repos (use multiple plugin &lt;execution&gt; sections with different prefixes for that). */
    @Parameter(defaultValue = "git.")
    private String prefix;

    /** Which string to use for `dirty` property. */
    @Parameter(defaultValue = "dirty")
    private String dirtyValue;

    /** Length of abbreviated SHA-1 for "shortRevision" and "shortParent" properties, min. 0, max. 40. */
    @Parameter(defaultValue = "7")
    private String shortRevisionLength;
    
    /** Which format to use for Git authorDate and Git commitDate. The default locale will be used. TimeZone can be specified, see {@link #dateFormatTimeZone}. */
    @Parameter(defaultValue = "yyyy-MM-dd")
    private String gitDateFormat;

    /** Which format to use for buildDate. The default locale will be used. TimeZone can be specified, see {@link #dateFormatTimeZone}. */
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
     * <pre>branch + "." + commitsCount + "/" + commitDate + "/" + shortRevision + (dirty.length > 0 ? "-" + dirty : "");</pre> */
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

    /** Print more information during build (e.g. parameters, all extracted properties, execution times). */
    @Parameter(defaultValue = "false")
    private boolean verbose;
    
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
        if (verbose) getLog().info("JGit BuildNumber Maven Plugin - start");
        long start = System.currentTimeMillis();

        executeImpl();

        long duration = System.currentTimeMillis() - start;
        if (verbose) getLog().info(String.format("JGit BuildNumber Maven Plugin - end (execution time: %d ms)", duration));
    }

    public void executeImpl() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            if (verbose) getLog().info("Execution of plugin is skipped by configuration.");
            return;
        }

        if (verbose) getLog().info("executionRootDirectory: " + executionRootDirectory + ", runOnlyAtExecutionRoot: " + runOnlyAtExecutionRoot
            + ", baseDirectory: " + baseDirectory + ", repositoryDirectory: " + repositoryDirectory);

        try {

            // executes only once per build
            // http://www.sonatype.com/people/2009/05/how-to-make-a-plugin-run-once-during-a-build/
            if (!runOnlyAtExecutionRoot || executionRootDirectory.equals(baseDirectory)) {

                long t = System.currentTimeMillis();
                BuildNumberExtractor extractor = new BuildNumberExtractor(repositoryDirectory);
                if (verbose) getLog().info("initializing Git repo, get base data: " + (System.currentTimeMillis() - t) + " ms");

                String headSha1 = extractor.getHeadSha1();
                String dirty = extractor.isGitStatusDirty() ? dirtyValue : null;

                List<String> params = Arrays.asList(headSha1, dirty, shortRevisionLength, gitDateFormat, buildDateFormat, dateFormatTimeZone,
                    countCommitsSinceInclusive, countCommitsSinceExclusive, buildNumberFormat);
                if (verbose) getLog().info("params: " + params);
                String paramsKey = "jgitParams" + prefix;
                String resultKey = "jgitResult" + prefix;

                // note: saving/loading custom classes doesn't work (due to different classloaders?, "cannot be cast" error);
                // when saving Properties object, our values don't survive; therefore we use a Map here
                Map<String, String> result = getCachedResultFromBuildConext(paramsKey, params, resultKey);
                if (result != null) {
                    if (verbose) getLog().info("using cached result");
                } else {
                    t = System.currentTimeMillis();
                    result = extractor.extract(shortRevisionLength, gitDateFormat, buildDateFormat, dateFormatTimeZone, countCommitsSinceInclusive,
                        countCommitsSinceExclusive, dirtyValue);
                    if (verbose) getLog().info("extracting properties for buildNumber: " + (System.currentTimeMillis() - t) + " ms");

                    if (buildNumberFormat != null) {
                        t = System.currentTimeMillis();
                        String jsBuildNumber = formatBuildNumberWithJS(result);
                        if (verbose) getLog().info("formatting buildNumber with JS: " + (System.currentTimeMillis() - t) + " ms");
                        result.put("buildNumber", jsBuildNumber); // overwrites default buildNumber
                    }
                    saveResultToBuildContext(paramsKey, params, resultKey, result);
                }

                getLog().info("BUILDNUMBER: " + result.get("buildNumber"));
                if (verbose) getLog().info("all extracted properties: " + result);
                setProperties(result, project.getProperties());

            } else if ("pom".equals(parentProject.getPackaging())) {
                // build started from parent, we are in subproject, lets provide parent properties to our project
                Properties parentProps = parentProject.getProperties();
                String revision = parentProps.getProperty(prefix + "revision");
                if (revision == null) {
                    // we are in subproject, but parent project wasn't build this time,
                    // maybe build is running from parent with custom module list - 'pl' argument
                    getLog().warn("Cannot extract Git info, maybe custom build with 'pl' argument is running");
                    fillPropsUnknown();
                    return;
                }
                if (verbose) getLog().info("using already extracted properties from parent module: " + toMap(parentProps));
                setProperties(parentProps, project.getProperties());

            } else {
                // should not happen
                getLog().warn("Cannot extract JGit version: something wrong with build process, we're not in parent, not in subproject!");
                fillPropsUnknown();
            }
        } catch (Exception e) {
            String message = e.getMessage() != null ? e.getMessage() : /* e.g. NPE */ e.getClass().getSimpleName();
            getLog().error(message);
            if (verbose) getLog().error(e); // stacktrace
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
            if (verbose) getLog().info("m2e incremental build detected");
            // getLog().info("buildContext.getClass(): " + buildContext.getClass()); // org.eclipse.m2e.core.internal.embedder.EclipseBuildContext
            List<String> cachedParams = (List<String>) buildContext.getValue(paramsKey);
            // getLog().info("cachedParams: " + cachedParams);
            if (Objects.equals(cachedParams, currentParams)) {
                Map<String, String> cachedResult = (Map<String, String>) buildContext.getValue(resultKey);
                // getLog().info("cachedResult: " + cachedResult);
                return cachedResult;
            }
        }
        return null;
    }

    private Map<String, String> toMap(Properties props) {
        Map<String, String> map = new TreeMap<>();
        for (String propertyName : propertyNames)
            map.put(propertyName, props.getProperty(prefix + propertyName));

        return map;
    }

    private void setProperties(Map<String, String> source, Properties target) {
        for (Map.Entry<String, String> e : source.entrySet())
            target.setProperty(prefix + e.getKey(), e.getValue());
    }

    private void setProperties(Properties source, Properties target) {
        for (String propertyName : propertyNames) {
            String prefixedName = prefix + propertyName;
            target.setProperty(prefixedName, source.getProperty(prefixedName));
        }
    }

    private void fillPropsUnknown() {
        Properties props = project.getProperties();
        for (String propertyName : propertyNames)
            props.setProperty(prefix + propertyName, "UNKNOWN-" + propertyName);
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

        for (Map.Entry<String, String> e : bn.entrySet())
            jsEngine.put(e.getKey(), e.getValue());
        Object res = jsEngine.eval(buildNumberFormat);
        if (res == null) throw new IllegalStateException("JS buildNumber is null");
        return res.toString();
    }
}
