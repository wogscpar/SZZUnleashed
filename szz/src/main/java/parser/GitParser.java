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

package parser;

import data.Issues;
import graph.AnnotationMap;
import graph.FileAnnotationGraph;
import java.io.*;
import java.util.*;
import java.util.stream.*;
import org.eclipse.jgit.api.BlameCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import util.CommitUtil;
import util.JSONUtil;

/**
 * A class which is capable to search and build line mapping graphs from a local repository. Uses
 * JGit to parse the repository and the revision trees.
 *
 * @author Oscar Svensson
 */
public class GitParser {

  private CommitUtil util;
  private Repository repo;
  private Issues issues;

  private String resultPath;
  private String DEFAULT_RES_PATH = "./results";

  private Logger logger;

  private int depth;

  /**
   * The constructor for the GitParser class. It requires the repository to exist and will fail if
   * its not. The resultPath is also created if it's not existing.
   *
   * @param path the path to where the local repository can be found.
   * @param resultPath the path to where the JSON files will be written.
   */
  public GitParser(String path, String resultPath, int depth, int customContext)
      throws IOException, GitAPIException {
    FileRepositoryBuilder builder = new FileRepositoryBuilder();
    builder.setMustExist(true);

    builder.addCeilingDirectory(new File(path));
    builder.findGitDir(new File(path));
    this.repo = builder.build();

    this.resultPath = resultPath;

    /*
     * Check if the resultpath exists otherwise create it.
     */

    if (this.resultPath != null) {
      File resDirectory = new File(resultPath);
      if (!resDirectory.exists()) resDirectory.mkdirs();
    } else {
      System.err.println("Resultpath not set! Using deafult directory instead.");
      this.resultPath = this.DEFAULT_RES_PATH;
    }

    this.util = new CommitUtil(this.repo, customContext);

    this.depth = depth;
  }

  public String getResultPath() {
    return this.resultPath;
  }

  public Repository getRepository() {
    return this.repo;
  }

  public Issues getIssues() {
    return this.issues;
  }

  public void useLogger(Logger logger) {
    this.logger = logger;
  }

  /**
   * Map lines between one commit and another.
   *
   * @param foundCommit a blameresult containing information about a commit that have made changes
   *     to a file.
   * @param filePath the file that the commit have made changes to.
   * @return a mapping with the original revision file lines as keys and the values the
   *     corresponding lines in the other commit.
   */
  private List<Integer> getLineMappings(BlameResult foundCommit, String filePath)
      throws IOException, GitAPIException {
    foundCommit.computeAll();
    RawText foundContent = foundCommit.getResultContents();

    /*
     * Easiest solution, maybe better with a list and a pair class?
     */
    List<Integer> lineMappings = new LinkedList<>();

    for (int line = 0; line < foundContent.size(); line++) {
      lineMappings.add(foundCommit.getSourceLine(line));
    }
    return lineMappings;
  }

  private int getSourceLine(BlameResult foundCommit, int index)
      throws IOException, GitAPIException {
    foundCommit.computeAll();

    try {
      return foundCommit.getSourceLine(index);
    } catch (ArrayIndexOutOfBoundsException e) {
      return -1;
    }
  }

  /**
   * Traces a file change that have occured before a given commmit.
   *
   * @param filePath specifies which file to trace changes on.
   * @param source the source commit from which the trace should start at.
   */
  private FileAnnotationGraph traceFileChanges(String filePath, Commit source, int step)
      throws IOException, GitAPIException {

    if (step == 0) return null;

    BlameCommand command = new BlameCommand(this.repo);

    /*
     * Save all line numbers for the source commits deletions.
     */
    List<Integer> delIndexes = null;
    if (source.diffWithParent.containsKey(filePath))
      delIndexes =
          source
              .diffWithParent
              .get(filePath)
              .deletions
              .stream()
              .map(s -> parseInt(s[0]))
              .collect(Collectors.toList());
    else return null;

    /*
     * Create a graph to store line mappings in.
     */
    FileAnnotationGraph graph = new FileAnnotationGraph();
    graph.filePath = filePath;
    graph.revisions = new LinkedList<>();
    graph.mappings = new HashMap<>();
    graph.sub_graphs = new HashMap<>();

    graph.revisions.add(ObjectId.toString(source.commit.toObjectId()));

    int index = 0;

    RevCommit parent = source.commit.getParent(0);
    command.setStartCommit(parent);
    command.setFilePath(filePath);

    BlameResult found = command.call();
    if (found == null) return graph;

    Map<RevCommit, Map<Integer, Integer>> foundRevisions = new HashMap<>();

    /*
     * Grab the blamed commits and get the line numbers.
     */
    for (int i = 0; i < delIndexes.size(); i++) {
      index = delIndexes.get(i);
      if (index == -1) continue;
      try {
        RevCommit foundRev = found.getSourceCommit(index);

        if (!foundRevisions.containsKey(foundRev)) {
          Map<Integer, Integer> blamedLines = new LinkedHashMap<>();

          blamedLines.put(index, getSourceLine(found, index));
          foundRevisions.put(foundRev, blamedLines);
        } else {
          foundRevisions.get(foundRev).put(index, getSourceLine(found, index));
        }
      } catch (Exception e) {
        // This means that a row didn't exist in a previous revision..
      }
    }

    /*
     * Save all mappings in the annotationgraph.
     */
    for (Map.Entry<RevCommit, Map<Integer, Integer>> rev : foundRevisions.entrySet()) {
      String revSha = ObjectId.toString(rev.getKey().toObjectId());

      if (!graph.mappings.containsKey(revSha)) {
        graph.revisions.add(revSha);
        graph.mappings.put(revSha, rev.getValue());
      } else {
        Map<Integer, Integer> linemapping = graph.mappings.get(revSha);
        // Add missing mappings.
        for (Map.Entry<Integer, Integer> entry : rev.getValue().entrySet()) {
          if (!linemapping.containsKey(entry.getKey())) {
            linemapping.put(entry.getKey(), entry.getValue());
          }
        }
      }
    }

    /*
     * Start building subgraphs.
     */
    for (Map.Entry<RevCommit, Map<Integer, Integer>> rev : foundRevisions.entrySet()) {
      Commit subCommit = this.util.getCommitDiffingLines(rev.getKey());
      FileAnnotationGraph subGraph = traceFileChanges(filePath, subCommit, step - 1);

      if (subGraph == null) break;
      graph.sub_graphs.put(subCommit.getHashString(), subGraph);
    }

    return graph;
  }

  /**
   * With each revision, check all files and build their line mapping graphs for each changed line.
   *
   * @param commits list of commits that should be traced.
   * @return the map containing annotation graphs for each file change by a commit.
   */
  private AnnotationMap<String, List<FileAnnotationGraph>> buildLineMappingGraph(
      List<Commit> commits) throws IOException, GitAPIException {

    AnnotationMap<String, List<FileAnnotationGraph>> fileGraph = new AnnotationMap<>();
    for (Commit commit : commits) {
      List<FileAnnotationGraph> graphs = new LinkedList<>();
      for (Map.Entry<String, DiffEntry.ChangeType> file : commit.changeTypes.entrySet()) {
        FileAnnotationGraph tracedCommits = traceFileChanges(file.getKey(), commit, this.depth);

        graphs.add(tracedCommits);
      }

      fileGraph.put(commit.getHashString(), graphs);
    }

    return fileGraph;
  }

  /**
   * Wrapper method to catch a faulty value.
   *
   * @param value the string to convert to an int.
   * @return the value of the string as an int.
   */
  private int parseInt(String value) {
    try {
      return Integer.parseInt(value);
    } catch (Exception e) {
      return -1;
    }
  }

  /**
   * Searchs for commits that have certain keywords in their messages, indicating that they have
   * fiexd bugs.
   *
   * <p>It then saves the found commits and the line mapping graph to two JSON files.
   *
   * @param commits a set containing references to commits.
   */
  public AnnotationMap<String, List<FileAnnotationGraph>> annotateCommits(Set<RevCommit> commits)
      throws IOException, GitAPIException {
    this.logger.info("Parsing difflines for all found commits.");
    List<Commit> parsedCommits = this.util.getDiffingLines(commits);

    this.logger.info("Saving parsed commits to file");
    JSONUtil.saveFoundCommits(parsedCommits, this.resultPath);

    this.logger.info("Building line mapping graph.");
    AnnotationMap<String, List<FileAnnotationGraph>> mapping = buildLineMappingGraph(parsedCommits);

    this.logger.info("Saving results to file");
    mapping.saveToJSON(this.resultPath);

    return mapping;
  }

  /**
   * Use this method to use already found big fixing changes.
   *
   * @param path the path to the json file where the changes are stored.
   */
  public Set<RevCommit> readBugFixCommits(String path) throws IOException, GitAPIException {
    if (repo == null) return Collections.emptySet();

    this.issues = new Issues();

    JSONParser commitParser = new JSONParser();
    try {
      JSONObject object = (JSONObject) commitParser.parse(new FileReader(path));

      this.issues.revisions = new HashSet<>();
      this.issues.dates = new HashMap<>();

      for (Object issue : object.keySet()) {
        Map<String, String> issueInfo = (Map<String, String>) object.get(issue);

        String rev = issueInfo.get("hash");
        RevCommit revCommit = this.repo.parseCommit(this.repo.resolve(rev));

        Map<String, String> dates = new HashMap<>();

        dates.put("resolutiondate", issueInfo.get("resolutiondate"));
        dates.put("commitdate", issueInfo.get("commitdate"));
        dates.put("creationdate", issueInfo.get("creationdate"));

        this.issues.dates.put(rev, dates);
        this.issues.revisions.add(revCommit);
      }

    } catch (FileNotFoundException | ParseException e) {
      return Collections.emptySet();
    }

    this.logger.info(String.format("Found %d number of commits.", this.issues.revisions.size()));

    if (this.issues.revisions.size() == 0) return Collections.emptySet();
    return this.issues.revisions;
  }

  /** Finds commits that indicates a bugfix and then builds a line mapping graph. */
  public Set<RevCommit> searchForBugFixes() throws IOException, GitAPIException {
    if (repo == null) {
      return Collections.emptySet();
    }

    SimpleCommitSearcher search = new SimpleCommitSearcher(this.repo);
    Set<RevCommit> foundCommits = search.filterOnBugPatterns();
    this.logger.info(String.format("Found %d number of commits", foundCommits.size()));

    if (foundCommits.size() == 0) {
      return Collections.emptySet();
    }
    return foundCommits;
  }
}
