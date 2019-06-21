package com.labun.buildnumber;

import java.io.File;
import java.util.Map;

import org.apache.tools.ant.Project;

import lombok.Getter;
import lombok.Setter;

/** Extracts Git metadata and creates build number. Publishes them as project properties. */
@Getter
@Setter // required by Ant task properties setter (note: Ant task "properties" are our "parameters") 
public class JGitBuildNumberAntTask implements Parameters {
    private Project project;

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

    public void execute() throws Exception {
        // set some parameters to Ant specific values
        if (getRepositoryDirectory() == null) setRepositoryDirectory(project.getBaseDir());
        
        validateAndSetParameterValues();

        if (skip) {
            project.log("Execution is skipped by configuration.");
            return;
        }

        Map<String, String> properties = new BuildNumberExtractor(this, msg -> project.log(msg)).extract();

        for (Map.Entry<String, String> property : properties.entrySet())
            project.setProperty(namespace + "." + property.getKey(), property.getValue());
    }
}
