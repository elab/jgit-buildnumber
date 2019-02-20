package ru.concerteza.util.buildnumber;

import java.io.File;
import java.io.IOException;

import org.gradle.api.DefaultTask;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.api.tasks.TaskAction;

public class JGitBuildNumberGradleTask extends DefaultTask {

	@TaskAction
	public void jGitBuildnumber_ExtractBuildnumber() throws IOException {
		BuildNumber buildNumber = BuildNumberExtractor.extract(new File("."));
		ExtraPropertiesExtension props = getProject().getExtensions().getExtraProperties();
		props.set("gitBranch", buildNumber.getBranch());
		props.set("gitCommitsCount", buildNumber.getCommitsCountAsString());
		props.set("gitTag", buildNumber.getTag());
		props.set("gitRevision", buildNumber.getRevision());
		props.set("gitShortRevision", buildNumber.getShortRevision());
		props.set("gitParent", buildNumber.getParent());
		props.set("gitAuthorDate", buildNumber.getAuthorDate());
		props.set("gitCommitDate", buildNumber.getCommitDate());
		props.set("gitBuildnumber", buildNumber.defaultBuildnumber());
	}
}
