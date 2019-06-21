package com.labun.buildnumber;

import java.io.File;
import java.util.Map;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

import lombok.Getter;
import lombok.Setter;

/** Extracts Git metadata and creates build number. Publishes them as project properties. */
@Getter
@Setter
public class JGitBuildNumberGradleTask extends DefaultTask implements Parameters {

    private String namespace;
    private String dirtyValue;
    private Integer shortRevisionLength;
    private String gitDateFormat;
    private String buildDateFormat;
    private String dateFormatTimeZone;
    private String countCommitsSinceInclusive;
    private String countCommitsSinceExclusive;
    private String countCommitsInPath;
    private String buildNumberFormat;
    private File repositoryDirectory;
    private Boolean runOnlyAtExecutionRoot;
    private Boolean skip;
    private Boolean verbose;

    @TaskAction
    public void extractBuildnumber() throws Exception {
        // set some parameters to Gradle specific values
        if (getRepositoryDirectory() == null) setRepositoryDirectory(getProject().getProjectDir());

        validateAndSetParameterValues();

        if (skip) {
            getLogger().lifecycle("Execution is skipped by configuration.");
            return;
        }

        Map<String, String> properties = new BuildNumberExtractor(this, msg -> getLogger().lifecycle(msg)).extract(); // "info" level will not be printed by default
        
        getProject().getExtensions().add(Map.class, namespace, properties);
    }
}
