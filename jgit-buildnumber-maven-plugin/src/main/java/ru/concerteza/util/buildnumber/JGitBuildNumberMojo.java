package ru.concerteza.util.buildnumber;

import java.io.File;
import java.util.Properties;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/** Goal which creates build number. */
@Mojo(name = "extract-buildnumber", defaultPhase = LifecyclePhase.VALIDATE)
public class JGitBuildNumberMojo extends AbstractMojo {

    @Parameter(property = "revisionProperty")
    private String revisionProperty = "git.revision";

    @Parameter(property = "shortRevisionProperty")
    private String shortRevisionProperty = "git.shortRevision";

    @Parameter(property = "branchProperty")
    private String branchProperty = "git.branch";

    @Parameter(property = "tagProperty")
    private String tagProperty = "git.tag";

    @Parameter(property = "parentProperty")
    private String parentProperty = "git.parent";

    @Parameter(property = "commitsCountProperty")
    private String commitsCountProperty = "git.commitsCount";

    @Parameter(property = "authorDateProperty")
    private String authorDateProperty = "git.authorDate";

    @Parameter(property = "commitDateProperty")
    private String commitDateProperty = "git.commitDate";

    @Parameter(property = "describeProperty")
    private String describeProperty = "git.describe";

    @Parameter(property = "buildDateProperty", readonly = true)
    private String buildDateProperty = "git.buildDate";

    @Parameter(property = "buildnumberProperty")
    private String buildnumberProperty = "git.buildnumber";

    /** Which format to use for Git authorDate and Git commitDate. */
    @Parameter(property = "gitDateFormat", defaultValue = "yyyy-MM-dd")
    private String gitDateFormat;

    /** Which format to use for buildDate. */
    @Parameter(property = "buildDateFormat", defaultValue = "yyyy-MM-dd HH:mm:ss")
    private String buildDateFormat;
    
    @Parameter(property = "javaScriptBuildnumberCallback")
    private String javaScriptBuildnumberCallback;

    /** Setting this parameter to 'false' allows to execute plugin in every submodule, not only in root one. */
    @Parameter(property = "runOnlyAtExecutionRoot", defaultValue = "true")
    private boolean runOnlyAtExecutionRoot;

    /** Setting this parameter to 'true' will skip plugin execution. */
    @Parameter(property = "skip", defaultValue = "false")
    private boolean skip;

    /** Directory to start searching git root from, should contain '.git' directory
     *  or be a subdirectory of such directory. '${project.basedir}' is used by default. */
    @Parameter(property = "repositoryDirectory", defaultValue = "${project.basedir}")
    private File repositoryDirectory;

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

    /** Extracts buildnumber fields from git repository and publishes them as maven properties.
     *  Executes only once per build. Return default (unknown) buildnumber fields on error. */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Execution of plugin is skipped by configuration.");
            return;
        }
        
        Properties props = project.getProperties();
        try {
            getLog().info("executionRootDirectory: " + executionRootDirectory + ", runOnlyAtExecutionRoot: " + runOnlyAtExecutionRoot + ", baseDirectory: " + baseDirectory + ", repositoryDirectory: " + repositoryDirectory);

            // executes only once per build
            // http://www.sonatype.com/people/2009/05/how-to-make-a-plugin-run-once-during-a-build/
            if (!runOnlyAtExecutionRoot || executionRootDirectory.equals(baseDirectory) 
                || (executionRootDirectory.equals(repositoryDirectory.getParentFile()) && runOnlyAtExecutionRoot )) {
                getLog().info("Getting git info from repositoryDirectory: " + repositoryDirectory);

                long startMillis = System.currentTimeMillis();
                
                // build started from this projects root
                BuildNumber bn = BuildNumberExtractor.extract(repositoryDirectory, gitDateFormat, buildDateFormat);
                props.setProperty(revisionProperty, bn.getRevision());
                props.setProperty(shortRevisionProperty, bn.getShortRevision());
                props.setProperty(branchProperty, bn.getBranch());
                props.setProperty(tagProperty, bn.getTag());
                props.setProperty(parentProperty, bn.getParent());
                props.setProperty(commitsCountProperty, bn.getCommitsCountAsString());
                props.setProperty(authorDateProperty, bn.getAuthorDate());
                props.setProperty(commitDateProperty, bn.getCommitDate());
                props.setProperty(describeProperty, bn.getDescribe());
                props.setProperty(buildDateProperty, bn.getBuildDate());
                // create composite buildnumber
                String composite = createBuildnumber(bn);
                props.setProperty(buildnumberProperty, composite);
                
                long durationMillis = System.currentTimeMillis() - startMillis;
                getLog().info(String.format(
                    "Git info extracted in %d ms, shortRevision: '%s', branch: '%s', tag: '%s', commitsCount: '%d', authorDate: '%s', commitDate: '%s', describe: '%s', buildDate: '%s', buildNumber: '%s'",
                    durationMillis, bn.getShortRevision(), bn.getBranch(), bn.getTag(), bn.getCommitsCount(), bn.getAuthorDate(), bn.getCommitDate(), bn.getDescribe(), bn.getBuildDate(), composite
                    ));
            } else if("pom".equals(parentProject.getPackaging())) {
                // build started from parent, we are in subproject, lets provide parent properties to our project
                Properties parentProps = parentProject.getProperties();
                String revision = parentProps.getProperty(revisionProperty);
                if(null == revision) {
                    // we are in subproject, but parent project wasn't build this time,
                    // maybe build is running from parent with custom module list - 'pl' argument
                    getLog().warn("Cannot extract Git info, maybe custom build with 'pl' argument is running");
                    fillPropsUnknown(props);
                    return;
                }
                props.setProperty(revisionProperty, revision);
                props.setProperty(shortRevisionProperty, parentProps.getProperty(shortRevisionProperty));
                props.setProperty(branchProperty, parentProps.getProperty(branchProperty));
                props.setProperty(tagProperty, parentProps.getProperty(tagProperty));
                props.setProperty(parentProperty, parentProps.getProperty(parentProperty));
                props.setProperty(commitsCountProperty, parentProps.getProperty(commitsCountProperty));
                props.setProperty(authorDateProperty, parentProps.getProperty(authorDateProperty));
                props.setProperty(commitDateProperty, parentProps.getProperty(commitDateProperty));
                props.setProperty(describeProperty, parentProps.getProperty(describeProperty));
                props.setProperty(buildDateProperty, parentProps.getProperty(buildDateProperty));
                props.setProperty(buildnumberProperty, parentProps.getProperty(buildnumberProperty));
            } else {
                // should not happen
                getLog().warn("Cannot extract JGit version: something wrong with build process, we're not in parent, not in subproject!");
                fillPropsUnknown(props);
            }
        } catch (Exception e) {
            getLog().error(e);
            fillPropsUnknown(props);
        }
    }

    private void fillPropsUnknown(Properties props) {
        props.setProperty(revisionProperty, "UNKNOWN_REVISION");
        props.setProperty(shortRevisionProperty, "UNKNOWN_REVISION");
        props.setProperty(branchProperty, "UNKNOWN_BRANCH");
        props.setProperty(tagProperty, "UNKNOWN_TAG");
        props.setProperty(parentProperty, "UNKNOWN_PARENT");
        props.setProperty(commitsCountProperty, "-1");
        props.setProperty(authorDateProperty, "UNKNOWN_AUTHOR_DATE");
        props.setProperty(commitDateProperty, "UNKNOWN_COMMIT_DATE");
        props.setProperty(describeProperty, "UNKNOWN_DESCRIBE");
        props.setProperty(buildDateProperty, "UNKNOWN_BUILD_DATE");
        props.setProperty(buildnumberProperty, "UNKNOWN_BUILDNUMBER");
    }

    private String createBuildnumber(BuildNumber bn) throws ScriptException {
        if(null != javaScriptBuildnumberCallback) return buildnumberFromJS(bn);
        return bn.defaultBuildnumber();
    }

    private String buildnumberFromJS(BuildNumber bn) throws ScriptException {
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
        jsEngine.put("revision", bn.getRevision());
        jsEngine.put("shortRevision", bn.getShortRevision());
        jsEngine.put("branch", bn.getBranch());
        jsEngine.put("tag", bn.getTag());
        jsEngine.put("parent", bn.getParent());
        jsEngine.put("commitsCount", bn.getCommitsCount());
        jsEngine.put("authorDate", bn.getAuthorDate());
        jsEngine.put("commitDate", bn.getCommitDate());
        jsEngine.put("describe", bn.getDescribe());
        Object res = jsEngine.eval(javaScriptBuildnumberCallback);
        if(null == res) throw new IllegalStateException("JS buildnumber callback returns null");
        return res.toString();
    }
}
