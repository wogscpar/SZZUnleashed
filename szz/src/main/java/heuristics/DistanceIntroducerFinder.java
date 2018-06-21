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

package heuristics;

import data.Issues;
import diff.DiffingLines.DiffLines;
import graph.AnnotationMap;
import graph.FileAnnotationGraph;
import info.debatty.java.stringsimilarity.Jaccard;
import java.io.*;
import java.util.*;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import parser.Commit;
import util.CommitUtil;

/**
 * A bug introduce finder which tries to check if a change is only cosmetic.
 *
 * @author Oscar Svensson
 */
public class DistanceIntroducerFinder implements BugIntroducerFinder {

  private Repository repo;
  private Issues issues;

  private CommitUtil util;

  /** An abstraction of a distance between two revisions. */
  private class RevisionDistance {
    public double distance;
    public List<String[]> updatedDiffLines;
  }

  public DistanceIntroducerFinder(Repository repo, int customContext) {

    this.util = new CommitUtil(repo, customContext);
  }

  private int parseInt(String value) {
    try {
      return Integer.parseInt(value);
    } catch (Exception e) {
      return -1;
    }
  }

  private RevCommit stringToRev(String rev) {
    RevCommit commit = null;
    try {
      commit = this.repo.parseCommit(this.repo.resolve(rev));
    } catch (Exception e) {
      return null;
    }
    return commit;
  }

  /**
   * Compute the Jaccard distance between two revisions.
   *
   * @param current a list containing the deletions made by a bugfixing commit. On the format
   *     [{lineIndex, Line},...]
   * @param other the line from the revision to compare with.
   * @param lineMapping the linemapping between the two revisions.
   * @return the distance between the two revisions and a list with the updated indexes.
   */
  private RevisionDistance compareTwoSections(
      List<String[]> current, List<String> other, Map<Integer, Integer> lineMapping) {

    RevisionDistance dist = new RevisionDistance();
    dist.updatedDiffLines = new LinkedList<>();
    Iterator<String[]> currentIterator = current.iterator();
    double distance = 0.0;

    Jaccard j2 = new Jaccard(2);

    while (currentIterator.hasNext()) {
      String[] entry = currentIterator.next();
      int lineId = parseInt(entry[0]);

      if (lineId >= 0) {
        int otherId = -1;
        try {
          otherId = lineMapping.get(lineId);

          if (entry[1] != null) {
            distance += j2.distance(entry[1], other.get(otherId));

            dist.updatedDiffLines.add(new String[] {Integer.toString(otherId), entry[1]});
          }
        } catch (Exception e) {
        }
      }
    }

    dist.distance = current.size() > 0 ? (distance / current.size()) : distance;

    return dist;
  }

  /**
   * Provided with a map with changes, the method finds potential bugintroducing commits by tracing
   * backwards all inserted and deleted lines.
   *
   * <p>All lines that have been
   */
  public List<String[]> findBugIntroducingCommits(
      AnnotationMap<String, List<FileAnnotationGraph>> graphs) throws IOException, GitAPIException {

    List<String[]> bugIntroducers = new LinkedList<>();
    for (Map.Entry<String, List<FileAnnotationGraph>> entry : graphs.entrySet()) {

      List<FileAnnotationGraph> files = new LinkedList<>();
      RevCommit sCommit = null;

      if ((sCommit = stringToRev(entry.getKey())) == null) continue;
      files = entry.getValue();
      Commit source = this.util.getCommitDiffingLines(sCommit);

      /*
       * Grep the first commit and check what have changed. Grab the insertions and the
       * deletions and trace back to when they was first introduced.
       *
       * The commit that introduced them the first time will be considered as the bug
       * introducing commit.
       */
      for (FileAnnotationGraph graph : files) {
        String[] fixBugPair = new String[2];
        fixBugPair[0] = entry.getKey();

        /*
         * Only check the lines that was removed.
         */
        DiffLines diffLines = source.diffWithParent.get(graph.filePath);
        List<String[]> deletions = diffLines.deletions;

        /*
         * Throw away the first revision aka the source commit.
         */
        Iterator<String> revisions = graph.revisions.iterator();
        revisions.next();
        if (!revisions.hasNext()) continue;

        /*
         * Extract the revision after the source. This revision should include the deleted lines.
         */
        String prevRevision = revisions.next();

        /*
         * Now check when the deleted lines where added aka when the lines changes between the
         * revisions.
         */
        double smallest = 1.0;
        String smallestDistCommit = prevRevision;

        while (revisions.hasNext()) {
          String revision = revisions.next();
          RevCommit next = null;

          if ((next = stringToRev(revision)) == null) continue;

          List<String> nextLines = this.util.getFileLines(next.getTree(), graph.filePath);

          Map<Integer, Integer> lineMapping = graph.getLineMapping(revision);

          /*
           * Pick the corresponding lines from the previous revisions.
           *
           * TODO: Make a better measurement and also check subgraphs, this measurement isn't
           * really valid.
           */
          RevisionDistance distance = compareTwoSections(deletions, nextLines, lineMapping);

          /*
           * The commit with a too low jaccard distance will be considered as the one the introduced
           * the deleted lines.
           */
          if (distance.distance < smallest) {
            smallest = distance.distance;
            smallestDistCommit = revision;
          }

          deletions = distance.updatedDiffLines;
        }
        fixBugPair[1] = smallestDistCommit;
        bugIntroducers.add(fixBugPair);
      }

      /*
       * TODO: Misses if semantics changes. Only checks as long as the line hasn't changed at
       * all. Check if the file has been modified between revisions.
       */
    }

    return bugIntroducers;
  }
}
