package com.labun.buildnumber;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class JGitBuildNumberGradlePlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        JGitBuildNumberGradleTask task = project.getTasks().replace("extract-buildnumber", JGitBuildNumberGradleTask.class);
    }
}
