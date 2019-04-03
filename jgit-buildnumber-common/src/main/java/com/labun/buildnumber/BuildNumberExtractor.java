package com.labun.buildnumber;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.errors.RevWalkException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.revplot.PlotWalk;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import lombok.Getter;

/** Extracts Git metadata and creates build number. See {@link #propertyNames}. */
public class BuildNumberExtractor {

    /** See documentation in README.md */
    static final List<String> propertyNames = Arrays.asList("revision", "shortRevision", "dirty", "branch", "tag", "parent", "shortParent", "commitsCount",
        "authorDate", "commitDate", "describe", "buildDate", "buildNumber");

    private static final String EMPTY_STRING = "";

    Parameters params;
    Logger logger;

    File gitDir;
    Git git;
    Repository repo;

    ObjectId headObjectId;
    private @Getter String headSha1;
    private @Getter boolean gitStatusDirty;

    void log(String msg) {
        logger.log(msg);
    }

    void logVerbose(String msg) {
        if (params.getVerbose()) logger.log(msg);
    }

    /** Initializes values from Git repo, which are always required, regardless of full or incremental build.
     * 
     * @param params input parameters
     * @param logger logger to log info messages
     * @throws Exception if git repo not found or cannot be read
     */
    public BuildNumberExtractor(Parameters params, Logger logger) throws Exception {
        this.params = params;
        this.logger = logger;

        long t = System.currentTimeMillis();

        params.validateAndSetParameterValues(); // defensive (parameters should have already been set and validated)
        logVerbose("params: " + params.asString());

        File repoDirectory = params.getRepositoryDirectory();
        if (!(repoDirectory.exists() && repoDirectory.isDirectory()))
            throw new IOException("Invalid repository directory provided: " + repoDirectory.getAbsolutePath());

        // (previously, jgit had some problems with not canonical paths; is it still the case?)
        File canonicalRepo = repoDirectory.getCanonicalFile();
        RepositoryBuilder builder = new RepositoryBuilder().findGitDir(canonicalRepo);

        gitDir = builder.getGitDir();
        logVerbose("gitDir=" + gitDir);
        git = Git.open(gitDir);
        repo = git.getRepository();

        Ref headRef = repo.exactRef(Constants.HEAD);
        if (headRef == null) throw new IOException("Cannot read current revision from repository: " + repo);

        headObjectId = headRef.getObjectId();
        headSha1 = headObjectId.name();

        // long t = System.currentTimeMillis();
        gitStatusDirty = !git.status().call().isClean();
        // System.out.println("dirty: " + gitStatusDirty + " (" + (System.currentTimeMillis() - t) + " ms)");

        logVerbose("repo state: " + "headSha1=" + headSha1 + ", gitStatusDirty=" + gitStatusDirty);
        logVerbose("initializing Git repo, get base data: " + (System.currentTimeMillis() - t) + " ms");
    }

    @Override
    protected void finalize() throws Throwable {
        git.close(); // also closes the `repo`
    }

    /** @return Map propertyName - propertyValue. See {@link #propertyNames}. */
    public Map<String, String> extract() throws Exception {
        long t = System.currentTimeMillis();

        try (RevWalk revWalk = new PlotWalk(repo)) {
            String branch = readCurrentBranch(headSha1);
            String tag = readTag(headSha1);

            RevCommit headCommit = revWalk.parseCommit(headObjectId);

            String parent = readParent(headCommit);
            String shortParent = readShortParent(headCommit, params.getShortRevisionLength());
            int commitsCount = countCommits(revWalk, headCommit, params.getCountCommitsSinceInclusive(), params.getCountCommitsSinceExclusive());

            DateFormat dfGitDate = new SimpleDateFormat(params.getGitDateFormat()); // default timezone, default locale
            if (params.getDateFormatTimeZone() != null) dfGitDate.setTimeZone(TimeZone.getTimeZone(params.getDateFormatTimeZone()));
            String authorDate = dfGitDate.format(headCommit.getAuthorIdent().getWhen());
            String commitDate = dfGitDate.format(headCommit.getCommitterIdent().getWhen());

            String describe = git.describe().setLong(true).call();

            SimpleDateFormat dfBuildDate = new SimpleDateFormat(params.getBuildDateFormat());
            if (params.getDateFormatTimeZone() != null) dfBuildDate.setTimeZone(TimeZone.getTimeZone(params.getDateFormatTimeZone()));
            String buildDate = dfBuildDate.format(new Date());

            String revision = headSha1;
            String shortRevision = abbreviateSha1(headSha1, params.getShortRevisionLength());
            String dirty = gitStatusDirty ? params.getDirtyValue() : "";
            String commitsCountAsString = Integer.toString(commitsCount);

            String buildNumber = defaultBuildNumber(tag, branch, commitsCountAsString, shortRevision, dirty);

            logVerbose("extracting properties for buildNumber: " + (System.currentTimeMillis() - t) + " ms");

            Map<String, String> res = new TreeMap<>();
            res.put("revision", revision);
            res.put("shortRevision", shortRevision);
            res.put("dirty", dirty);
            res.put("branch", branch);
            res.put("tag", tag);
            res.put("parent", parent);
            res.put("shortParent", shortParent);
            res.put("commitsCount", commitsCountAsString);
            res.put("authorDate", authorDate);
            res.put("commitDate", commitDate);
            res.put("describe", describe);
            res.put("buildDate", buildDate);
            res.put("buildNumber", buildNumber);

            if (params.getBuildNumberFormat() != null) {
                t = System.currentTimeMillis();
                String jsBuildNumber = formatBuildNumberWithJS(res);
                logVerbose("formatting buildNumber with JS: " + (System.currentTimeMillis() - t) + " ms");
                res.put("buildNumber", jsBuildNumber); // overwrites default buildNumber
            }

            logVerbose("all extracted properties: " + res);
            log("BUILDNUMBER: " + res.get("buildNumber"));

            // ensure all properties are set
            for (String property : propertyNames)
                if (res.get(property) == null) throw new RuntimeException("Property '" + property + "' is not set");

            return res;
        }

    }

    private static String abbreviateSha1(String sha1, int length) {
        return (sha1 != null && sha1.length() > length) ? sha1.substring(0, length) : sha1;
    }

    public String defaultBuildNumber(String tag, String branch, String commitsCount, String shortRevision, String dirty) {
        String name = (tag.length() > 0) ? tag : (branch.length() > 0) ? branch : "UNNAMED";
        return name + "." + commitsCount + "." + shortRevision + (dirty.length() > 0 ? "-" + dirty : "");
    }

    private String readCurrentBranch(String headSha1) throws IOException {
        String branch = repo.getBranch();
        // should not happen
        if (null == branch) return EMPTY_STRING;
        if (headSha1.equals(branch)) return EMPTY_STRING;
        return branch;
    }

    private String readTag(String sha1) {
        Map<String, SortedSet<String>> tagMap = loadTagsMap();
        SortedSet<String> tags = tagMap.get(sha1);
        if (tags == null) return EMPTY_STRING;
        return String.join(";", tags);
    }

    private static String readParent(RevCommit commit) {
        if (commit == null) return EMPTY_STRING;
        RevCommit[] parents = commit.getParents();
        if (parents == null || parents.length == 0) return EMPTY_STRING;
        return Stream.of(parents).map(p -> p.getId().name()/*SHA-1*/).collect(Collectors.joining(";"));
    }

    private static String readShortParent(RevCommit commit, int length) {
        if (commit == null) return EMPTY_STRING;
        RevCommit[] parents = commit.getParents();
        if (parents == null || parents.length == 0) return EMPTY_STRING;
        return Stream.of(parents).map(p -> abbreviateSha1(p.getId().name()/*SHA-1*/, length)).collect(Collectors.joining(";"));
    }

    /** @return Map sha1 - tag names */
    private Map<String, SortedSet<String>> loadTagsMap() {
        Map<String, Ref> refMap = repo.getTags(); // key: short tag name ("v1.0"), value: ref with full tag name ("refs/tags/v1.0")
        Map<String, SortedSet<String>> res = new HashMap<>(refMap.size());
        for (Map.Entry<String, Ref> entry : refMap.entrySet()) {
            String sha1 = extractPeeledSha1(entry.getValue());
            res.computeIfAbsent(sha1, k -> new TreeSet<>()).add(entry.getKey());
        }
        return res;
    }

    /** @param tagRef tag (annotated or lightweight)
     *  @return SHA-1 corresponding to the tag */
    private String extractPeeledSha1(Ref tagRef) {
        Ref peeled = repo.peel(tagRef);
        if (peeled.getPeeledObjectId() != null) return peeled.getPeeledObjectId().name(); // annotated tag
        else return peeled.getObjectId().name(); // lightweight tag
    }

    private int countCommits(RevWalk walk, RevCommit headCommit, String countCommitsSinceInclusive, String countCommitsSinceExclusive) throws Exception {
        try {
            // walk.reset(); // only needed if iterator has been accessed before
            walk.setRetainBody(false);
            walk.markStart(headCommit);
            int res = 0;
            if (countCommitsSinceInclusive != null) {
                String ancestorSha1 = getSha1(countCommitsSinceInclusive);
                for (RevCommit commit : walk) { res += 1; if (commit.getId().getName().startsWith(ancestorSha1)) return res; }
                throw new IllegalArgumentException("commit '" + countCommitsSinceInclusive + "' not found (parameter 'countCommitsSinceInclusive')");
            } else if (countCommitsSinceExclusive != null) {
                String ancestorSha1 = getSha1(countCommitsSinceExclusive);
                for (RevCommit commit : walk) { if (commit.getId().getName().startsWith(ancestorSha1)) return res; res += 1; }
                throw new IllegalArgumentException("commit '" + countCommitsSinceExclusive + "' not found (parameter 'countCommitsSinceExclusive')");
            } else {
                for (RevCommit commit : walk) { res += 1; }
            }
            return res;
        } catch (RevWalkException ex) {
            // ignore exception thrown by JGit when walking shallow clone, return -1 to indicate shallow
            return -1;
        }
    }

    /** If the parameter is a tag, returns SHA-1 of the commit it points to; otherwise returns the parameter unchanged. 
     * @param tagOrSha1 tag (annotated or lightweight) or SHA-1 (complete or abbreviated) 
     * @return SHA-1 (complete or abbreviated) */
    private String getSha1(String tagOrSha1) throws Exception {
        Ref ref = repo.exactRef(Constants.R_TAGS + tagOrSha1);
        if (ref == null) return tagOrSha1; // SHA-1
        return extractPeeledSha1(ref); // tag
    }

    private String formatBuildNumberWithJS(Map<String, String> bnProperties) throws ScriptException {
        String engineName = "JavaScript";
        // find JavaScript engine using context class loader
        ScriptEngine jsEngine = new ScriptEngineManager().getEngineByName(engineName);
        if (jsEngine == null) {
            // may be null when running within Eclipse using m2e, maybe due to OSGi class loader;
            // this does work in Eclipse, see ScriptEngineManager constructor Javadoc for what passing a null means here
            jsEngine = new ScriptEngineManager(null).getEngineByName(engineName);
        }
        if (jsEngine == null) {
            log(engineName + " not found!");
            return "UNKNOWN_JS_BUILDNUMBER";
        }

        for (Map.Entry<String, String> e : bnProperties.entrySet())
            jsEngine.put(e.getKey(), e.getValue());
        Object res = jsEngine.eval(params.getBuildNumberFormat());
        if (res == null) throw new IllegalStateException("JS buildNumber is null");
        return res.toString();
    }
}
