package ru.concerteza.util.buildnumber;

import java.io.File;

import org.gradle.api.DefaultTask;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.api.tasks.TaskAction;

public class JGitBuildNumberGradleTask extends DefaultTask {

    @TaskAction
    public void jGitBuildnumber_ExtractBuildnumber() throws Exception {
        BuildNumber bn = BuildNumberExtractor.extract(new File("."), "yyyy-MM-dd", "yyyy-MM-dd HH:mm:ss");
        ExtraPropertiesExtension props = getProject().getExtensions().getExtraProperties();
        props.set("gitRevision", bn.getRevision());
        props.set("gitShortRevision", bn.getShortRevision());
        props.set("gitBranch", bn.getBranch());
        props.set("gitTag", bn.getTag());
        props.set("gitParent", bn.getParent());
        props.set("gitCommitsCount", bn.getCommitsCountAsString());
        props.set("gitAuthorDate", bn.getAuthorDate());
        props.set("gitCommitDate", bn.getCommitDate());
        props.set("gitDescribe", bn.getDescribe());
        props.set("gitBuildDate", bn.getBuildDate());
        props.set("gitBuildnumber", bn.defaultBuildnumber());
    }
}
