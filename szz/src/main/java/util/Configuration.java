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

import heuristics.BugFinderFactory;
import java.util.*;
import org.apache.commons.cli.*;
import org.slf4j.Logger;

/**
 * Global configuration file. Contains all commandline options.
 *
 * @author Oscar Svensson
 */
public class Configuration {

  private static Configuration instance = null;

  private int depth = 3;
  private int cpus = 1;
  private int bugFinder = BugFinderFactory.SIMPLE;
  private int diffCustomContext = 0;

  private String issuePath = null;
  private String repoPath = null;
  private String resPath = "results";
  private String partialFixPattern = "fix";

  private boolean omitLineText = false;

  public String helpHeader = "Commandline options for the SZZ algorithm.";
  public String helpFooter = "The results will be produced in ./results";

  protected Configuration() {}

  public static Configuration getInstance() {
    if (instance == null) {
      throw new IllegalStateException("Configuration not initialized!");
    }
    return instance;
  }

  public static Configuration init(Logger logger, String... args) {
    if (instance != null) {
      throw new IllegalStateException("Configuration already intialized!");
    }

    instance = new Configuration();

    CommandLineParser parser = new DefaultParser();
    CommandLine cmd = null;
    Options options = getCMDOptions();
    try {
      cmd = parser.parse(options, args);
    } catch (ParseException e) {
      logger.warn(e.getMessage());
      System.exit(1);
    }

    if (cmd.hasOption("h")) {
      HelpFormatter helpForm = new HelpFormatter();
      helpForm.printHelp("SZZ", instance.getHelpHeader(), options, instance.getHelpFooter(), true);
      System.exit(0);
    }

    if (cmd.hasOption("i")) {
      instance.setIssuePath(cmd.getOptionValue("i"));
    } else {
      logger.warn("No Issues specified! Please use -i <IssuePath>");
      System.exit(1);
    }

    if (cmd.hasOption("r")) {
      instance.setRepository(cmd.getOptionValue("r"));
    } else {
      logger.warn("No Repository specified! Please use -r <RepoPath>");
      System.exit(1);
    }

    if (cmd.hasOption("d")) {
      instance.setDepth(Integer.parseInt(cmd.getOptionValue("d")));
    }

    logger.info("Checking available processors...");
    if (cmd.hasOption("c")) {
      instance.setNumberOfCPUS(Integer.parseInt(cmd.getOptionValue("c")));
      logger.info(String.format("Using %s cpus!", instance.getNumberOfCPUS()));
    } else {
      instance.setNumberOfCPUS(Runtime.getRuntime().availableProcessors());
      logger.info(String.format("Found %s processes!", instance.getNumberOfCPUS()));
    }

    if (cmd.hasOption("b")) {
      if (cmd.getOptionValue("b") == "distance") instance.setBugFinder(BugFinderFactory.DISTANCE);
    }

    if (cmd.hasOption("dc")) {
      instance.setDiffCustomContext(Integer.parseInt(cmd.getOptionValue("dc")));
    }

    if (cmd.hasOption("p")) {
      instance.setPartialFixPattern(cmd.getOptionValue("p"));
    }

    if (cmd.hasOption("olt")) {
      instance.setOmitLineText(true);
    }

    return instance;
  }

  public String getHelpFooter() {
    return helpFooter;
  }

  public String getHelpHeader() {
    return helpHeader;
  }

  public int getDepth() {
    return depth;
  }

  protected void setDepth(int depth) {
    this.depth = depth;
  }

  public String getIssuePath() {
    return issuePath;
  }

  protected void setIssuePath(String issuePath) {
    this.issuePath = issuePath;
  }

  public String getRepository() {
    return repoPath;
  }

  protected void setRepository(String repoPath) {
    this.repoPath = repoPath;
  }

  public int getNumberOfCPUS() {
    return cpus;
  }

  protected void setNumberOfCPUS(int cpus) {
    this.cpus = cpus;
  }

  public int getBugFinder() {
    return bugFinder;
  }

  protected void setBugFinder(int bugFinder) {
    this.bugFinder = bugFinder;
  }

  public int getDiffCustomContext() {
    return diffCustomContext;
  }

  protected void setDiffCustomContext(int diffCustomContext) {
    this.diffCustomContext = diffCustomContext;
  }

  public String getResultPath() {
    return resPath;
  }

  protected void setResultPath(String resPath) {
    this.resPath = resPath;
  }

  public String getPartialFixPattern() {
    return partialFixPattern;
  }

  protected void setPartialFixPattern(String pattern) {
    this.partialFixPattern = pattern;
  }

  public boolean getOmitLineText() {
    return this.omitLineText;
  }

  protected void setOmitLineText(boolean omitLineText) {
    this.omitLineText = omitLineText;
  }

  private static Options getCMDOptions() {
    Options options = new Options();

    Option help_option = new Option("h", false, "Print help message");
    help_option.setRequired(false);
    options.addOption(help_option);

    Option issue_option = new Option("i", true, "Path to the issue file.");
    issue_option.setRequired(false);
    options.addOption(issue_option);

    Option repo_option = new Option("r", true, "Path to a local git repository.");
    repo_option.setRequired(false);
    options.addOption(repo_option);

    Option depth_option = new Option("d", true, "Depth for the line mapping graph.");
    depth_option.setRequired(false);
    options.addOption(depth_option);

    Option cpu_option = new Option("c", true, "The number of cpus. Defaults to all.");
    cpu_option.setRequired(false);
    options.addOption(cpu_option);

    Option bugFinderOption =
        new Option("b", true, "The choice of bugfinder. Either simple or distance.");
    bugFinderOption.setRequired(false);
    options.addOption(bugFinderOption);

    Option diffCustomContextOption =
        new Option("dc", true, "How many lines the differ adds around a diff.");
    diffCustomContextOption.setRequired(false);
    options.addOption(diffCustomContextOption);

    Option partialFixPatternOption =
        new Option(
            "p",
            true,
            "Specify the pattern that should be used when maching bug fixes. Defaults to \"fix\"");
    partialFixPatternOption.setRequired(false);
    options.addOption(partialFixPatternOption);

    Option omitLineTextOption =
        new Option("olt", false, "Only output the line numbers in the annotation graph.");
    omitLineTextOption.setRequired(false);
    options.addOption(omitLineTextOption);

    return options;
  }
}
