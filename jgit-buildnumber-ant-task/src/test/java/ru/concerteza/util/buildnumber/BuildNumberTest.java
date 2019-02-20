package ru.concerteza.util.buildnumber;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;

public class BuildNumberTest {

    BuildNumber buildNumber;

    @Before
    public void beforeEachTest() throws IOException {
	buildNumber = new BuildNumber("1234567890", "branch", "tag", "parent", 37, "2019-01-01", "2019-02-20");
    }

    @Test
    public void returnsCorrectNumberOfGitCommitsCount() throws IOException {
	assertThat(buildNumber.getCommitsCountAsString(), is("37"));
    }

    @Test
    public void returnsCorrectBranch() throws IOException {
	assertThat(buildNumber.getBranch(), is("branch"));
    }

    @Test
    public void returnsCorrectParent() throws IOException {
	assertThat(buildNumber.getParent(), is("parent"));
    }

    @Test
    public void returnsCorrectRevision() throws IOException {
	assertThat(buildNumber.getRevision(), is("1234567890"));
    }

    @Test
    public void returnsCorrectTag() throws IOException {
	assertThat(buildNumber.getTag(), is("tag"));
    }

    @Test
    public void returnsShortRevision() throws IOException {
	assertThat(buildNumber.getShortRevision(), is("1234567"));
    }

    @Test
    public void returnsCorrectDefaultBuildNumber() throws IOException {
	assertThat(buildNumber.defaultBuildnumber(), is("tag.37.1234567"));
    }

    @Test
    public void setsBranchAsNameIfTagIsNotPresent() throws IOException {
	BuildNumber buildNumber = new BuildNumber("1234567890", "branch", "", "parent", 37, "2019-01-01", "2019-02-20");
	assertThat(buildNumber.defaultBuildnumber(), is("branch.37.1234567"));
    }

    @Test
    public void setsUNNAMEDAsNameIdTagAndBranchAreNotPresent() throws IOException {
	BuildNumber buildNumber = new BuildNumber("1234567890", "", "", "parent", 37, "2019-01-01", "2019-02-20");
	assertThat(buildNumber.defaultBuildnumber(), is("UNNAMED.37.1234567"));
    }
}
