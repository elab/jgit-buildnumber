package com.labun.buildnumber;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class JGitBuildNumberGradlePlugin implements Plugin<Project> {

    public void apply(Project target) {
        target.getTasks().replace("jGitBuildnumber_ExtractBuildnumber", JGitBuildNumberGradleTask.class);
    }
}
