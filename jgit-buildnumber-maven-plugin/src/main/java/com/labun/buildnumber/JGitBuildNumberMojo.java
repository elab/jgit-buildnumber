package com.labun.buildnumber;

import static com.labun.buildnumber.BuildNumberExtractor.propertyNames;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.TreeMap;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.sonatype.plexus.build.incremental.BuildContext;

import lombok.Getter;
import lombok.Setter;

/** Extracts Git metadata and creates build number. Publishes them as project properties. */
@Getter
@Setter
@Mojo(name = "extract-buildnumber", defaultPhase = LifecyclePhase.VALIDATE)
public class JGitBuildNumberMojo extends AbstractMojo implements Parameters {

    @Component
    private BuildContext buildContext;

    // ---------- parameters (user configurable) ----------

    private @Parameter String namespace;
    private @Parameter String dirtyValue;
    private @Parameter Integer shortRevisionLength;
    private @Parameter String gitDateFormat;
    private @Parameter String buildDateFormat;
    private @Parameter String dateFormatTimeZone;
    private @Parameter String countCommitsSinceInclusive;
    private @Parameter String countCommitsSinceExclusive;
    private @Parameter String buildNumberFormat;
    private @Parameter File repositoryDirectory;
    private @Parameter Boolean runOnlyAtExecutionRoot;
    private @Parameter Boolean skip;
    private @Parameter Boolean verbose;

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
        long start = System.currentTimeMillis();

        // set some parameters to Maven specific values
        if (getRepositoryDirectory() == null) setRepositoryDirectory(project.getBasedir()); // ${project.basedir}

        validateAndSetParameterValues();

        if (skip) {
            getLog().info("Execution is skipped by configuration.");
            return;
        }

        if (verbose) getLog().info("JGit BuildNumber Maven Plugin - start");
        if (verbose) getLog().info("executionRootDirectory: " + executionRootDirectory + ", baseDirectory: " + baseDirectory);

        try {
            // accesses Git repo only once per build
            // http://www.sonatype.com/people/2009/05/how-to-make-a-plugin-run-once-during-a-build/
            if (!runOnlyAtExecutionRoot || executionRootDirectory.equals(baseDirectory)) {

                BuildNumberExtractor extractor = new BuildNumberExtractor(this, msg -> getLog().info(msg));

                String headSha1 = extractor.getHeadSha1();
                String dirty = extractor.isGitStatusDirty() ? dirtyValue : null;

                List<Object> params = Arrays.asList(headSha1, dirty, shortRevisionLength, gitDateFormat, buildDateFormat, dateFormatTimeZone,
                    countCommitsSinceInclusive, countCommitsSinceExclusive, buildNumberFormat);
                String paramsKey = "jgitParams" + namespace;
                String resultKey = "jgitResult" + namespace;

                // note: saving/loading custom classes doesn't work (due to different classloaders?, "cannot be cast" error);
                // when saving Properties object, our values don't survive; therefore we use a Map here
                Map<String, String> result = getCachedResultFromBuildConext(paramsKey, params, resultKey);
                if (result != null) {
                    if (verbose) getLog().info("using cached result: " + result);
                } else {
                    result = extractor.extract();
                    saveResultToBuildContext(paramsKey, params, resultKey, result);
                }
                setProperties(result, project.getProperties());

            } else if ("pom".equals(parentProject.getPackaging())) {
                // build started from parent, we are in subproject, lets provide parent properties to our project
                Properties parentProps = parentProject.getProperties();
                String revision = parentProps.getProperty(namespace + "." + "revision");
                if (revision == null) {
                    // we are in subproject, but parent project wasn't build this time,
                    // maybe build is running from parent with custom module list - 'pl' argument
                    getLog().warn("Cannot extract Git info, maybe custom build with 'pl' argument is running");
                    fillPropsUnknown(); // TODO: throw exception instead?
                    return;
                }
                if (verbose) getLog().info("using already extracted properties from parent module: " + toMap(parentProps));
                setProperties(parentProps, project.getProperties());

            } else {
                // should not happen
                getLog().warn("Cannot extract JGit version: something wrong with build process, we're not in parent, not in subproject!");
                fillPropsUnknown(); // TODO: throw exception instead?
            }
        } catch (Exception e) {
            String message = e.getMessage() != null ? e.getMessage() : /* e.g. NPE */ e.getClass().getSimpleName();
            getLog().error(message);
            // if (verbose) getLog().error(e); // stacktrace (can be printed by Maven with debug output)
            // fillPropsUnknown();
            throw new MojoFailureException(message, e);
        } finally {
            long duration = System.currentTimeMillis() - start;
            if (verbose) getLog().info(String.format("JGit BuildNumber Maven Plugin - end (execution time: %d ms)", duration));
        }
    }

    // m2e build? => save extracted values to BuildContext
    private void saveResultToBuildContext(String paramsKey, List<Object> currentParams, String resultKey, Map<String, String> result) {
        if (buildContext != null) {
            buildContext.setValue(paramsKey, currentParams);
            buildContext.setValue(resultKey, result);
        }
    }

    // m2e incremental build and input params (HEAD, etc.) not changed? => try to get previously extracted values from BuildContext
    // note: buildContext != null only in m2e builds in Eclipse
    private Map<String, String> getCachedResultFromBuildConext(String paramsKey, List<Object> currentParams, String resultKey) {
        if (buildContext != null && buildContext.isIncremental()) {
            if (verbose) getLog().info("m2e incremental build detected");
            // getLog().info("buildContext.getClass(): " + buildContext.getClass()); // org.eclipse.m2e.core.internal.embedder.EclipseBuildContext
            List<Object> cachedParams = (List<Object>) buildContext.getValue(paramsKey);
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
            map.put(propertyName, props.getProperty(namespace + "." + propertyName));

        return map;
    }

    private void setProperties(Map<String, String> source, Properties target) {
        for (Map.Entry<String, String> e : source.entrySet())
            target.setProperty(namespace + "." + e.getKey(), e.getValue());
    }

    private void setProperties(Properties source, Properties target) {
        for (String propertyName : propertyNames) {
            String prefixedName = namespace + "." + propertyName;
            target.setProperty(prefixedName, source.getProperty(prefixedName));
        }
    }

    private void fillPropsUnknown() {
        Properties props = project.getProperties();
        for (String propertyName : propertyNames)
            props.setProperty(namespace + "." + propertyName, "UNKNOWN-" + propertyName);
    }
}
