package com.labun.buildnumber;

import java.io.File;
import java.util.Map;

import org.apache.tools.ant.Project;

/** Extracts Git metadata and creates build number. Publishes them as Ant properties. See {@link JGitBuildNumberAntTask#execute()}. */
public class JGitBuildNumberAntTask {
    private Project project;

    /** @param project Ant project setter */
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
     *     <li>{@code git.dirty}</li>
     *     <li>{@code git.branch}</li>
     *     <li>{@code git.tag}</li>
     *     <li>{@code git.parent}</li>
     *     <li>{@code git.commitsCount}</li>
     *     <li>{@code git.authorDate}</li>
     *     <li>{@code git.commitDate}</li>
     *     <li>{@code git.describe}</li>
     *     <li>{@code git.buildDate}</li>
     *     <li>{@code git.buildNumber}</li>
     * </ul>
     * @throws Exception if git repo not found or cannot be read
     */
    public void execute() throws Exception {
        String repoDirString = project.getProperty("git.repositoryDirectory");
        File repoDir = null != repoDirString ? new File(repoDirString) : new File(".");
        String gitDateFormat = project.getProperty("git.gitDateFormat");
        String buildDateFormat = project.getProperty("git.buildDateFormat");
        String dateFormatTimeZone = null;
        String countCommitsSinceInclusive = project.getProperty("git.countCommitsSinceInclusive");
        String countCommitsSinceExclusive = project.getProperty("git.countCommitsSinceExclusive");
        String dirtyValue = project.getProperty("git.dirtyValue");

        Map<String, String> properties = new BuildNumberExtractor(repoDir).extract(gitDateFormat, buildDateFormat, dateFormatTimeZone,
            countCommitsSinceInclusive, countCommitsSinceExclusive, dirtyValue);

        for (Map.Entry<String, String> property : properties.entrySet())
            project.setProperty("git." + property.getKey(), property.getValue());
    }
}
