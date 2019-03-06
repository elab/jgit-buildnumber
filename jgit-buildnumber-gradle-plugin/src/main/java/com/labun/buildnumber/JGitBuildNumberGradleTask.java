package com.labun.buildnumber;

import java.io.File;
import java.util.Map;

import org.eclipse.jgit.util.StringUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.api.tasks.TaskAction;

/** <pre>
gitRevision
gitShortRevision
gitDirty
gitBranch
gitTag
gitParent
gitCommitsCount
gitAuthorDate 
gitCommitDate 
gitDescribe
gitBuildDate
gitBuildNumber
</pre> */
public class JGitBuildNumberGradleTask extends DefaultTask {

    @TaskAction
    public void jGitBuildNumber_ExtractBuildNumber() throws Exception {
        File repoDir = new File(".");
        String gitDateFormat = "yyyy-MM-dd";
        String buildDateFormat = "yyyy-MM-dd HH:mm:ss";
        String dateFormatTimeZone = null;
        String countCommitsSinceInclusive = null;
        String countCommitsSinceExclusive = null;
        String dirtyValue = "dirty";

        Map<String, String> bn = new BuildNumberExtractor(repoDir).extract(gitDateFormat, buildDateFormat, dateFormatTimeZone, countCommitsSinceInclusive,
                countCommitsSinceExclusive, dirtyValue);
        ExtraPropertiesExtension props = getProject().getExtensions().getExtraProperties();

        for (Map.Entry<String, String> e : bn.entrySet())
            props.set("git" + StringUtils.capitalize(e.getKey()), e.getValue());
    }
}
