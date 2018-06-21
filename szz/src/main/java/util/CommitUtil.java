/*
 * MIT License
 *
 * Copyright (c) 2018 Axis Communications AB
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package util;

import data.Issues;
import diff.DiffingLines;
import diff.DiffingLines.DiffLines;
import java.io.*;
import java.util.*;
import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import parser.Commit;

/**
 * Util to perform specific operations on commits.
 *
 * @author Oscar Svensson
 */
public class CommitUtil {

  private Git git;
  private Issues issues;
  private Repository repo;

  private int customContext;

  public CommitUtil(Repository repo, int customContext) {
    this.repo = repo;

    this.git = new Git(repo);

    this.customContext = customContext;
  }

  /**
   * Method to read a file from a specific revision.
   *
   * @param tree the revision tree that contains the file.
   * @param path the path that leads to the file in the tree.
   * @return a list containing all lines in the file.
   */
  public List<String> getFileLines(RevTree tree, String path) throws IOException, GitAPIException {

    try (TreeWalk walk = new TreeWalk(this.repo)) {
      walk.addTree(tree);
      walk.setRecursive(true);
      walk.setFilter(PathFilter.create(path));

      walk.next();
      ObjectId oId = walk.getObjectId(0);

      if (oId == ObjectId.zeroId()) {
        return new LinkedList<>();
      }

      ObjectLoader loader = this.repo.open(oId);

      ByteArrayOutputStream stream = new ByteArrayOutputStream();
      loader.copyTo(stream);

      return IOUtils.readLines(new ByteArrayInputStream(stream.toByteArray()), "UTF-8");
    } catch (Exception e) {
      return new LinkedList<>();
    }
  }

  /**
   * Find all lines that diffs with a commits parent commit.
   *
   * @param commits a set of unique commits.
   */
  public List<Commit> getDiffingLines(Set<RevCommit> commits) throws IOException, GitAPIException {
    List<Commit> parsedCommits = new LinkedList<>();

    for (RevCommit revc : commits) {
      Commit commit = getCommitDiffingLines(revc);

      if (commit == null) continue;

      if (!commit.diffWithParent.isEmpty()) parsedCommits.add(commit);
    }

    return parsedCommits;
  }

  /**
   * Extracts the differences between two revisions.
   *
   * @param a revision a.
   * @param b revision b.
   * @return a list containing all diffs.
   */
  public List<DiffEntry> diffRevisions(RevCommit a, RevCommit b)
      throws IOException, GitAPIException {
    return this.git
        .diff()
        .setOldTree(getCanonicalTreeParser(a))
        .setNewTree(getCanonicalTreeParser(b))
        .call();
  }

  /**
   * Extract a list containing all Edits that exists between two revisions.
   *
   * @param entry a diffentry which contains information about a diff between two revisions.
   * @return an EditList containing all Edits.
   */
  public EditList getDiffEditList(DiffEntry entry) throws IOException, GitAPIException {
    DiffFormatter form = new DiffFormatter(DisabledOutputStream.INSTANCE);
    form.setRepository(this.git.getRepository());

    FileHeader fh = form.toFileHeader(entry);
    return fh.toEditList();
  }

  /**
   * Extract filechanges between one revision and another. The resulting lines are formatted in a
   * git diff format. Each line starts with the line number separated with added or removerd
   * indicating if its the old or new file.
   *
   * @param newTree the new revision that contains the new changes.
   * @param oldTree the old revision that contains the old changes.
   * @param entry an DiffEntry that contains the number of changes between the newTree and the
   *     oldTree.
   * @return a list containing all diffing lines.
   */
  public DiffLines diffFile(DiffEntry entry)
      throws IOException, GitAPIException {
    EditList edits = getDiffEditList(entry);

    DiffingLines differ = new DiffingLines(this.repo, this.customContext);

    return differ.getDiffingLines(entry, edits);
  }

  /**
   * Parse the lines a commit recently made changes to compared to its parent.
   *
   * @param revc the current revision.
   * @return a commit object containing all differences.
   */
  public Commit getCommitDiffingLines(RevCommit revc, RevCommit... revother)
      throws IOException, GitAPIException {

    if (revc.getId() == revc.zeroId()) return null;

    RevCommit parent = null;
    if (revother.length > 0) parent = revother[0];
    else if (revc.getParents().length > 0) parent = revc.getParent(0);
    else parent = revc;

    if (parent.getId() == ObjectId.zeroId()) return null;

    List<DiffEntry> diffEntries = diffRevisions(parent, revc);

    Commit commit = new Commit(revc);

    for (DiffEntry entry : diffEntries) {
      DiffLines changedLines = diffFile(entry);

      commit.diffWithParent.put(entry.getNewPath(), changedLines);
      commit.changeTypes.put(entry.getNewPath(), entry.getChangeType());
    }
    return commit;
  }

  /**
   * Returns a revision tree parser wich could be used to compare revisions and extract revision
   * files.
   *
   * @param commitId a unique ID for a commit in the repository.
   * @return a tree iterator that could iterate through the revision tree.
   */
  private AbstractTreeIterator getCanonicalTreeParser(ObjectId commitId) throws IOException {
    try (RevWalk walk = new RevWalk(this.git.getRepository())) {
      RevCommit commit = walk.parseCommit(commitId);
      ObjectId treeId = commit.getTree().getId();
      try (ObjectReader reader = git.getRepository().newObjectReader()) {
        return new CanonicalTreeParser(null, reader, treeId);
      }
    }
  }
}
