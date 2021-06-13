package com.labun.buildnumber;

import java.io.File;

import lombok.Data;

@Data
public class SimpleParameters implements Parameters {

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

}
