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

import graph.AnnotationMap;
import graph.FileAnnotationGraph;
import heuristics.BugFinderFactory;
import heuristics.BugIntroducerFinder;
import java.io.*;
import java.util.*;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.Configuration;
import util.JSONUtil;

/**
 * A thread that starts a parser instance. It also logs the activity of the parser
 * and prints the current state.
 *
 * @author Oscar Svensson
 */
public class GitParserThread extends Thread {
  public GitParser parser;
  private String issues;
  private int bugFinder;
  private int depth;

  private Logger logger = LoggerFactory.getLogger(GitParserThread.class);

  public GitParserThread(
      String repo, String issues, String results, int bugFinder, int depth, int customContext) {
    try {
      this.parser = new GitParser(repo, results, depth, customContext);
      this.issues = issues;

      this.bugFinder = bugFinder;
    } catch (IOException e) {
      e.printStackTrace();
    } catch (GitAPIException e) {
      e.printStackTrace();
    }

    // this.contextMap = MDC.getCopyOfContextMap();
    this.depth = depth;
  }

  public GitParserThread(int id, String issues) {
    Configuration conf = Configuration.getInstance();
    try {
      this.parser =
          new GitParser(
              conf.getRepository(),
              String.format("%s/result%d", conf.getResultPath(), id),
              conf.getDepth(),
              conf.getDiffCustomContext());
      this.issues = issues;
      this.bugFinder = conf.getBugFinder();
    } catch (IOException e) {
      e.printStackTrace();
    } catch (GitAPIException e) {
      e.printStackTrace();
    }
  }

  public void run() {
    this.parser.useLogger(this.logger);

    logger.info("Started process...");
    try {
      Set<RevCommit> commits = this.parser.readBugFixCommits(this.issues);
      logger.info("Checking each commits diff...");

      AnnotationMap<String, List<FileAnnotationGraph>> graphs =
          this.parser.annotateCommits(commits);
      logger.info("Trying to find potential bug introducing commits...");
      List<String[]> bugIntroducers = Collections.emptyList();

      BugIntroducerFinder finder =
          BugFinderFactory.getFinder(this.parser.getRepository(), this.parser.getIssues());

      bugIntroducers = finder.findBugIntroducingCommits(graphs);

      logger.info("Saving found bug introducing commits...");
      JSONUtil.saveBugIntroducingCommits(bugIntroducers, this.parser.getResultPath());

    } catch (IOException e) {
      e.printStackTrace();
    } catch (GitAPIException e) {
      e.printStackTrace();
    }
  }
}
