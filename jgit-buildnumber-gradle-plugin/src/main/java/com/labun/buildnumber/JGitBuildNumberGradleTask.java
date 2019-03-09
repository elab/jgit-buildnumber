package com.labun.buildnumber;

import java.io.File;
import java.util.Map;

import org.eclipse.jgit.util.StringUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.api.tasks.TaskAction;

/** Extracts Git metadata and creates build number. Publishes them as following project properties:
<pre>
gitRevision
gitShortRevision
gitDirty
gitBranch
gitTag
gitParent
gitShortParent
gitCommitsCount
gitAuthorDate
gitCommitDate
gitDescribe
gitBuildDate
gitBuildNumber
</pre>
 */
public class JGitBuildNumberGradleTask extends DefaultTask {

    @TaskAction
    public void jGitBuildNumber_ExtractBuildNumber() throws Exception {
        File repoDir = new File(".");
        String gitDateFormat = "yyyy-MM-dd";
        String buildDateFormat = "yyyy-MM-dd HH:mm:ss";
        String dateFormatTimeZone = null;
        String countCommitsSinceInclusive = null;
        String countCommitsSinceExclusive = null;
        String prefix = "git";
        String dirtyValue = "dirty";
        String shortRevisionLength = "7";

        Map<String, String> properties = new BuildNumberExtractor(repoDir).extract(shortRevisionLength, gitDateFormat, buildDateFormat, dateFormatTimeZone,
            countCommitsSinceInclusive, countCommitsSinceExclusive, dirtyValue);
        ExtraPropertiesExtension props = getProject().getExtensions().getExtraProperties();

        for (Map.Entry<String, String> property : properties.entrySet())
            props.set(prefix + StringUtils.capitalize(property.getKey()), property.getValue());
    }
}
