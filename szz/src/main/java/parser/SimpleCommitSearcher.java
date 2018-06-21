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

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import util.Configuration;

/**
 * Simple class which does a search in a git repository for potential bug fixing commits.
 *
 * @author Oscar Svensson
 */
public class SimpleCommitSearcher {

  private Git git;
  private Repository repo;

  public List<Matcher> bugpatterns;

  /**
   * Constructor using a path to a local repository.
   */
  public SimpleCommitSearcher(String repoPath) throws IOException, GitAPIException {
    Configuration conf = Configuration.getInstance();
    FileRepositoryBuilder builder = new FileRepositoryBuilder();
    builder.setMustExist(true);

    builder.addCeilingDirectory(new File(repoPath));
    builder.findGitDir(new File(repoPath));
    this.repo = builder.build();
    this.git = new Git(repo);

    bugpatterns = Arrays.asList(compile("JENKINS\\-[0-9]"));
  }

  /**
   * Constructor using a repository.
   */
  public SimpleCommitSearcher(Repository repo) {
    this.repo = repo;
    this.git = new Git(repo);

    bugpatterns = Arrays.asList(compile("JENKINS\\-[0-9]"));
  }

  /**
   * Compile a regex pattern.
   *
   * @param pattern a regex like string.
   * @return a matcher object that can be used to match bugs.
   */
  private Matcher compile(String pattern) {
    Pattern p = Pattern.compile(pattern, Pattern.DOTALL);
    return p.matcher("");
  }

  /**
   * Method to count the number of commits in a repository. If the repository is null, this method
   * returns -1.
   */
  public int countCommits() throws IOException, GitAPIException {
    if (repo == null || git == null) {
      return -1;
    }

    Iterable<RevCommit> logs = git.log().call();

    int count = 0;
    for (RevCommit recv : logs) {
      count++;
    }

    return count;
  }

  /**
   * Iterator through all commits and find all commits that matches the specific pattern.
   *
   * @return a set containing commits that matches the bugpatterns.
   */
  public Set<RevCommit> filterOnBugPatterns() throws IOException, GitAPIException {
    Iterable<RevCommit> logs = this.git.log().call();
    Set<RevCommit> foundCommits = new HashSet<>();

    int number = 0;
    for (Matcher bug_matcher : bugpatterns) {
      for (RevCommit recv : logs) {
        bug_matcher.reset(recv.getFullMessage());

        if (bug_matcher.find()) {
          foundCommits.add(recv);
        }
      }
    }

    return foundCommits;
  }
}
