package ru.concerteza.util.buildnumber;

import java.io.File;

import org.apache.tools.ant.Project;

/**
 * Ant task, extracts buildnumber fields from git repository and publishes them as ant properties
 *
 * @author alexey
 * Date: 11/16/11
 * @see BuildNumber
 * @see BuildNumberExtractor
 */
public class JGitBuildNumberAntTask {
    private Project project;

    /**
     * @param project ant project setter
     */
    public void setProject(Project project) {
        this.project = project;
    }

    /**
     * Reads {@code git.repositoryDirectory} property that should contain '.git' directory
     * or be a subdirectory of such directory. If property wasn't set current work directory is used instead.
     * Extracted properties names:
     * <ul>
     *     <li>{@code git.revision}</li>
     *     <li>{@code git.shortRevision}</li>
     *     <li>{@code git.branch}</li>
     *     <li>{@code git.tag}</li>
     *     <li>{@code git.parent}</li>
     *     <li>{@code git.commitsCount}</li>
     *     <li>{@code git.authorDate}</li>
     *     <li>{@code git.commitDate}</li>
     *     <li>{@code git.describe}</li>
     *     <li>{@code git.buildDate}</li>
     *     <li>{@code git.buildnumber}</li>
     * </ul>
     * @throws Exception if git repo not found or cannot be read
     */
    public void execute() throws Exception {
        String repoDirString = project.getProperty("git.repositoryDirectory");
        File repoDir = null != repoDirString ? new File(repoDirString) :  new File(".");
        BuildNumber bn = BuildNumberExtractor.extract(repoDir, "yyyy-MM-dd", "yyyy-MM-dd HH:mm:ss");
        project.setProperty("git.revision", bn.getRevision());
        project.setProperty("git.shortRevision", bn.getShortRevision());
        project.setProperty("git.branch", bn.getBranch());
        project.setProperty("git.tag", bn.getTag());
        project.setProperty("git.parent", bn.getParent());
        project.setProperty("git.commitsCount", bn.getCommitsCountAsString());
        project.setProperty("git.authorDate", bn.getAuthorDate());
        project.setProperty("git.commitDate", bn.getCommitDate());
        project.setProperty("git.describe", bn.getDescribe());
        project.setProperty("git.buildDate", bn.getBuildDate());
        project.setProperty("git.buildnumber", bn.defaultBuildnumber());
    }
}
