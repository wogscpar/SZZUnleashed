"""
Script that extracts general data about a git repository.
"""
__author__ = "Oscar Svensson"
__copyright__ = "Copyright (c) 2018 Axis Communications AB"
__license__ = "MIT"

import csv
import json
import re

from argparse import ArgumentParser
from datetime import datetime
from numpy import median, mean
from pygit2 import Repository

def has_added(message):
    """
    Function to check if a message contains any word that indicates an addition of lines of code.
    """
    if (re.search(
            r"add(?:ed)*|implement(?:ed)*|introduce(?:d)*|improve(?:ment|ments)*",
            message.lower())):
        return True
    return False


def has_updated(message):
    """
    Function to check if a message contains any word that indicates an update of lines of code.
    """
    if (re.search(
            r"update[d]*|mov(?:ing|e|ed)|refactor|modifying|switching|deprecate(?:d)*|"+
            "clean(?:up|ed)*",
            message.lower())):
        return True
    return False


def has_bugfix(message):
    """
    Function to check if a message contains any word that indicates a bug fix.
    """
    if (re.search(r"jenkins[-]?\d|hudson[-]?\d|fix(?:es|ed)*|solve(?:d)*",
                  message.lower())):
        return True
    return False


def has_issue(message):
    """
    Function to check if a message contains any word that indicates a issue.
    """
    if re.search(r"issue number", message.lower()):
        return True
    return False


def save_commit_messages(commits, repo):
    """
    Function to run some statistics on a number of commits in a git repository.
    """

    mapping = {}

    added = set()
    updated = set()
    bugfix = set()
    issue_set = set()
    for commit in commits:
        message = commit.message
        mapping[commit.hex] = commit.message

        if has_added(message):
            added.add(commit.hex)
        elif has_updated(message):
            updated.add(commit.hex)
        elif has_bugfix(message):
            bugfix.add(commit.hex)
        elif has_issue(message):
            issue_set.add(commit.hex)

    """
    Dumps all found commits to a file.
    """
    with open("./results/commit_messages.json", 'w') as output:
        json.dump(mapping, output)

    overall = set()
    overall.update(added)
    overall.update(updated)
    overall.update(bugfix)
    overall.update(issue_set)

    all_messages = set([commit.hex for commit in commits])
    not_defined = {c: repo.get(c).message for c in all_messages - overall}

    print("Number of commits that added something: {} ({}%)".format(
        len(added),
        float(len(added)) / len(all_messages)))
    print("Number of commits that updated something: {} ({}%)".format(
        len(updated),
        float(len(updated)) / len(all_messages)))
    print("Number of commits that fixed a bug: {} ({}%)".format(
        len(bugfix),
        float(len(bugfix)) / len(all_messages)))
    print("Number of commits that contained an issue number: {} ({}%)".format(
        len(issue_set),
        float(len(issue_set)) / len(all_messages)))

    """
    Dumps all undefined commits to a file as well.
    """
    with open("./results/undefined_commit_messages.json", 'w') as output:
        json.dump(not_defined, output)
    print("Number of undefined commits: {} ({}%)".format(
        len(not_defined),
        float(len(not_defined)) / len(all_messages)))


def get_average_time_issues(issue_path):
    """
    Function to get the average times for issues.
    """
    issues_dict = {}
    with open(issue_path, 'r') as inp:
        issues_dict = json.load(inp)

    days = []

    lowest = (float('Inf'), 0, 0)
    highest = (0, None, None)

    for _, dates in issues_dict.items():
        creationdate = dates['creationdate']
        resolutiondate = dates['resolutiondate']

        creationdate = datetime.strptime(
            creationdate, "%Y-%m-%d %H:%M:%S %z").replace(tzinfo=None)
        resolutiondate = datetime.strptime(
            resolutiondate, "%Y-%m-%d %H:%M:%S %z").replace(tzinfo=None)

        days.append(((resolutiondate - creationdate).days))
        if days[-1] > highest[0]:
            highest = (days[-1], creationdate, resolutiondate)
        if days[-1] < lowest[0]:
            lowest = (days[-1], creationdate, resolutiondate)

    print("Lowest: {}".format(lowest))
    print("Highest: {}".format(highest))
    print("Mean time between resolution date and commit date: {} days".format(
        mean(days)))


def get_general_data(repo_path, issue_path, labels, pairs):
    """
    Function to get general statistics for a git repository.
    """
    repo = Repository(repo_path)

    issue_list = {}
    labeled_commits = {}

    with open(labels, 'r') as inp:
        reader = csv.reader(inp)
        next(reader)

        for commit in reader:
            labeled_commits[commit[0]] = float(commit[1])

    print("Number of commits: {}".format(len(labeled_commits)))
    print("Number of found bugintroducing commits: {}".format(
        len([
            labeled_commits[f] for f in labeled_commits
            if labeled_commits[f] > 0
        ])))

    pair_map = []
    with open(pairs, 'r') as inp:
        pair_map = json.load(inp)

    total_fixes = set([p[0] for p in pair_map])
    print("Total number of fixes used: {}".format(len(total_fixes)))

    bug_labeled_commits = set(
        [l for l in labeled_commits if labeled_commits[l] > 0])

    fixes_in_bugs = set(bug_labeled_commits).intersection(total_fixes)
    print("Total number of fixes in bugs found : {}".format(
        len(fixes_in_bugs)))

    time_diff = []
    for pair in pair_map:
        fix = repo.get(pair[0])
        bug = repo.get(pair[1])

        fix_date = datetime.fromtimestamp(fix.commit_time).replace(tzinfo=None)
        bug_date = datetime.fromtimestamp(bug.commit_time).replace(tzinfo=None)

        diff = (fix_date - bug_date).days

        time_diff.append(diff)
    years, days = divmod(float(mean(time_diff)), 365.25)
    myears, mdays = divmod(float(median(time_diff)), 365.25)

    print(
        "Average time between bug introduction and fix: {} years and {} days".
        format(years, days))
    print("Median time between bug introduction and fix: {} years and {} days".
          format(myears, mdays))

    with open(issue_path, 'r') as inp:
        issue_list = json.load(inp)

    print("Total number of fixes found: {}".format(len(issue_list)))

    save_commit_messages([repo.get(c) for c in bug_labeled_commits], repo)
    get_average_time_issues(issue_path)


if __name__ == "__main__":
    PARSER = ArgumentParser(
        description="Utility to extract purpose features from" +
        " a repository or a single commit.")

    PARSER.add_argument(
        "--repository",
        "-r",
        type=str,
        default="./repos/jenkins",
        help="Path to local git repository.")
    PARSER.add_argument(
        "--issues",
        "-i",
        type=str,
        default="../szz/issue_list_saved.json",
        help="Issues to analyze.")
    PARSER.add_argument(
        "--labels",
        "-l",
        type=str,
        default="./labels.csv",
        help="Found labels.")
    PARSER.add_argument(
        "--fixinpairs",
        "-fp",
        type=str,
        default="./fix_and_introducers_pairs.json",
        help="File with fix and introducing pair commits.")

    ARGS = PARSER.parse_args()
    REPO_PATH = ARGS.repository
    ISSUES = ARGS.issues
    LABELS = ARGS.labels
    PAIRS = ARGS.fixinpairs

    get_general_data(REPO_PATH, ISSUES, LABELS, PAIRS)
