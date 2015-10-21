package ru.concerteza.util.buildnumber;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Extracts buildnumber fields from git repository. Put it here, not in common module, because we don't want
 * any ant task dependencies except jgit and ant
 *
 * @author alexey
 * Date: 11/16/11
 * @see BuildNumber
 */
public class BuildNumberExtractor {
    private static final String EMPTY_STRING = "";

    /**
     * @param repoDirectory directory to start searching git root from, should contain '.git' directory
     *                      or be a subdirectory of such directory
     * @return extracted buildnumber object
     * @throws IOException
     */
    public static BuildNumber extract(File repoDirectory) throws IOException, GitAPIException {
        if(!(repoDirectory.exists() && repoDirectory.isDirectory())) throw new IOException(
                "Invalid repository directory provided: " + repoDirectory.getAbsolutePath());
        // open repo, jgit has some problems with not canonical paths
        File canonicalRepo = repoDirectory.getCanonicalFile();
        Git git = Git.open(canonicalRepo);
        Repository repo = git.getRepository();
        try {
            // extract HEAD revision
            ObjectId revisionObject = repo.resolve(Constants.HEAD);
            if (null == revisionObject) throw new IOException("Cannot read current revision from repository: " + repo);
            String revision = revisionObject.name();
            // extract current branch
            String branch = readCurrentBranch(repo, revision);
            // extract current tag
            String tag = readCurrentTag(repo, revision);
            // extract current parent
            String parent = readCurrentParent(repo, revision);
            // count total commits
            int commitsCount = countCommits(repo, revisionObject);
            // extract date of current commit
            String commitDate = readCurrentCommitDate(repo, revision);
            // extract describe result of current commit
            String describe = readCurrentDescribe(git);
            return new BuildNumber(revision, branch, tag, parent, commitsCount, commitDate, describe);
        } finally {
            repo.close();
        }
    }

    private static String readCurrentBranch(Repository repo, String revision) throws IOException {
        String branch = repo.getBranch();
        // should not happen
        if (null == branch) return EMPTY_STRING;
        if (revision.equals(branch)) return EMPTY_STRING;
        return branch;
    }

    private static String readCurrentTag(Repository repo, String revision) {
        Map<String, String> tagMap = loadTagsMap(repo);
        String tag = tagMap.get(revision);
        if (null == tag) return EMPTY_STRING;
        return tag;
    }

    private static String readCurrentParent(Repository repo, String revision) throws IOException {
        ObjectId rev = repo.resolve(revision);
        if (null == rev) return EMPTY_STRING;
        RevWalk rw = new RevWalk(repo);
        RevCommit commit = rw.parseCommit(rev);
        RevCommit[] parents = commit.getParents();
        if (null == parents || parents.length == 0) return EMPTY_STRING;
        rw.dispose();
        String parentsFormat = null;
        for (RevCommit p : parents) {
            String sha1 = p.getId().name();
            if (null == parentsFormat) {
                parentsFormat = sha1;
            } else {
                parentsFormat += ";" + sha1;
            }
        }
        return parentsFormat;
    }

    private static String readCurrentCommitDate(Repository repo, String revision) throws IOException {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        ObjectId rev = repo.resolve(revision);
        if (null == rev) return EMPTY_STRING;
        RevWalk rw = new RevWalk(repo);
        RevCommit commit = rw.parseCommit(rev);
        PersonIdent author = commit.getAuthorIdent();
        Date commitDate = author.getWhen();
        return df.format(commitDate);
    }

    private static String readCurrentDescribe(Git git) throws IOException, GitAPIException {
        return git.describe().setLong(true).call();
    }

    // sha1 -> tag name
    private static Map<String, String> loadTagsMap(Repository repo) {
        Map<String, Ref> refMap = repo.getTags();
        Map<String, String> res = new HashMap<String, String>(refMap.size());
        for (Map.Entry<String, Ref> en : refMap.entrySet()) {
            String sha1 = extractPeeledSha1(repo, en.getValue());
            String existed = res.get(sha1);
            final String value;
            if (null == existed) {
                value = en.getKey();
            } else {
                value = existed + ";" + en.getKey();
            }
            res.put(sha1, value);
        }
        return res;
    }

    // search for sha1 corresponding to annotated tag
    private static String extractPeeledSha1(Repository repo, Ref ref) {
        Ref peeled = repo.peel(ref);
        ObjectId oid = peeled.getPeeledObjectId();
        return null != oid ? oid.name() : peeled.getObjectId().name();
    }

    // takes about 1 sec to count 69939 in intellijidea repo
    private static int countCommits(Repository repo, ObjectId revision) throws IOException {
        RevWalk walk = new RevWalk(repo);
        walk.setRetainBody(false);
        RevCommit head = walk.parseCommit(revision);
        walk.markStart(head);
        int res = 0;
        for (RevCommit commit : walk) res += 1;
        walk.dispose();
        return res;
    }
}
