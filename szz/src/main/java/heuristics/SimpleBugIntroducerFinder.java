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
import graph.AnnotationMap;
import graph.FileAnnotationGraph;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import util.RevisionCombinationGenerator;

/**
 * A simple bug introduce finder as described by Zeller et al.
 *
 * @author Oscar Svensson
 */
public class SimpleBugIntroducerFinder implements BugIntroducerFinder {

  private Issues issues;
  private Repository repo;
  private int depth;
  private Pattern partialFixPattern;

  public SimpleBugIntroducerFinder(
      Issues issues, Repository repo, int depth, String partialFixPattern) {
    this.issues = issues;
    this.repo = repo;
    this.depth = depth;

    this.partialFixPattern = Pattern.compile(partialFixPattern);
  }

  /**
   * Check if a commit is within a fix timeframe.
   *
   * @param fix the commit containing the fix.
   * @param commit the potential bug introducing commit.
   * @return if the commit is within the timeframe.
   */
  private boolean isWithinTimeframe(String fix, String commit) throws IOException, GitAPIException {
    Map<String, String> dates = this.issues.get(fix);

    RevCommit rCommit = this.repo.parseCommit(this.repo.resolve(commit));

    Date revisionDate = rCommit.getCommitterIdent().getWhen();

    String commitDateString = dates.get("creationdate");
    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");

    Date commitDate = null;
    try {
      commitDate = format.parse(commitDateString);
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }

    return revisionDate.before(commitDate);
  }

  /** Check if a commit is a partial fix. */
  private boolean isPartialFix(String commit) throws IOException, GitAPIException {
    RevCommit rCommit = this.repo.parseCommit(this.repo.resolve(commit));
    String message = rCommit.getFullMessage();

    Matcher fixMatch = this.partialFixPattern.matcher(commit);

    return fixMatch.find();
  }

  private Collection<FileAnnotationGraph> getSubGraphs(
      Collection<FileAnnotationGraph> root, int depth) {
    Collection<FileAnnotationGraph> sub = new LinkedList<>();
    if (depth == 1) return sub;

    for (FileAnnotationGraph subGraph : root) {
      if (depth > 2) {
        sub.addAll(getSubGraphs(subGraph.sub_graphs.values(), depth - 1));
        sub.addAll(subGraph.sub_graphs.values());
      }
      sub.add(subGraph);
    }
    return sub;
  }

  /**
   * Simple heuristics of the SZZ algorithm. Pick all commits that have made changes to a line but
   * take into consideration if they have been made before or after the bug was reported.
   *
   * @param graphs a graph containing all reported bugfixes.
   */
  public List<String[]> findBugIntroducingCommits(
      AnnotationMap<String, List<FileAnnotationGraph>> graphs) throws IOException, GitAPIException {

    List<String[]> bugIntroducers = new LinkedList<>();
    List<String[]> potentialBugIntroducers = new LinkedList<>();

    Map<String, List<String>> bucketIntroducers = new HashMap<String, List<String>>();
    Map<String, List<String>> bucketIssues = new HashMap<String, List<String>>();

    for (Map.Entry<String, List<FileAnnotationGraph>> entry : graphs.entrySet()) {

      List<FileAnnotationGraph> files = new LinkedList<>();
      String sCommitString = entry.getKey();
      files = entry.getValue();

      /*
       * Grab all commits that are seen as fixes or that have changed anything.
       * Only checks the first layer of commits.
       */
      Collection<FileAnnotationGraph> subGraphs = getSubGraphs(files, this.depth);
      subGraphs.addAll(files);

      for (FileAnnotationGraph fileGraph : subGraphs) {
        Iterator<String> revisions = fileGraph.revisions.iterator();
        revisions.next();
        if (!revisions.hasNext()) continue;

        while (revisions.hasNext()) {
          String rev = revisions.next();
          String[] pair = new String[2];
          pair[0] = sCommitString;
          pair[1] = rev;

          /*
           * Check if the timestamp is within the timeframe or not.
           */
          if (isWithinTimeframe(sCommitString, rev)) {
            bugIntroducers.add(pair);
          } else {
            if (!bucketIntroducers.containsKey(fileGraph.filePath)) {
              bucketIntroducers.put(fileGraph.filePath, new ArrayList<>());
            }
            bucketIntroducers.get(fileGraph.filePath).add(rev);

            if (!bucketIssues.containsKey(fileGraph.filePath)) {
              bucketIssues.put(fileGraph.filePath, new ArrayList<>());
            }
            bucketIssues.get(fileGraph.filePath).add(sCommitString);
          }
        }
      }
    }

    List<String[]> partial_fix_suspects = new LinkedList<>();
    Map<String, List<String>> partialIntroducers = new HashMap<String, List<String>>();
    Map<String, List<String>> partialIssues = new HashMap<String, List<String>>();
    /*
     * Now check if any of the potential bugintroducing commits are bugintroducers for any other fix commit, aka weak suspects.
     * This check should be made smarter...
     */
    for (Map.Entry<String, List<String>> entry : bucketIntroducers.entrySet()) {
      List<String> introducers = entry.getValue();
      List<String> issues = bucketIssues.get(entry.getKey());

      RevisionCombinationGenerator gen = new RevisionCombinationGenerator(introducers, issues, 2);
      gen = gen.iterator();

      while(gen.hasNext()) {
        String[] pair = gen.getNextIndic();
        if (pair[0] == "" && pair[1] == "")
          continue;
        
        if (isWithinTimeframe(pair[1], pair[0])) {
          bugIntroducers.add(pair);
        } else {

          if (!partialIntroducers.containsKey(entry.getKey())) {
            partialIntroducers.put(entry.getKey(), new ArrayList<>());
          }
          partialIntroducers.get(entry.getKey()).add(pair[0]);

          if (!partialIssues.containsKey(entry.getKey())) {
            partialIssues.put(entry.getKey(), new ArrayList<>());
          }
          partialIssues.get(entry.getKey()).add(pair[1]);
        }
      }
    }

    /*
     * Now check for partial fixes. If a commit is flagged as a fix, it is a candidate to be a partial fix.
     */
    for (Map.Entry<String, List<String>> suspects : partialIntroducers.entrySet()) {
      List<String> introducers = suspects.getValue();
      List<String> issues = partialIssues.get(suspects.getKey());

      RevisionCombinationGenerator gen = new RevisionCombinationGenerator(introducers, issues, 2);
      gen = gen.iterator();

      while(gen.hasNext()) {
        String[] pair = gen.getNextIndic();
        if (pair[0] == "" && pair[1] == "")
          continue;
        if (isPartialFix(pair[0])) {
          bugIntroducers.add(pair);
        }
      }
    }

    /*
     * All other pairs that hasn't been flagged as bug introducers are said to be hard suspects.
     */

    return bugIntroducers;
  }
}
