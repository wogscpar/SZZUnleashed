"""
Script to extract the purpose features.
"""
__author__ = "Oscar Svensson"
__copyright__ = "Copyright (c) 2018 Axis Communications AB"
__license__ = "MIT"

import csv
import re

from argparse import ArgumentParser
from tqdm import tqdm
from pygit2 import Repository, GIT_SORT_TOPOLOGICAL, GIT_SORT_REVERSE

PATTERNS = [r"bug", r"fix", r"defect", r"patch"]

def is_fix(message):
    """
    Check if a message contains any of the fix patterns.
    """
    for pattern in PATTERNS:
        if re.search(pattern, message):
            return True
    return False

def get_purpose_features(repo_path, branch):
    """
    Extract the purpose features for each commit.
    """
    repo = Repository(repo_path)
    head = repo.references.get(branch)

    commits = list(
        repo.walk(head.target, GIT_SORT_TOPOLOGICAL | GIT_SORT_REVERSE))

    features = []
    for _, commit in enumerate(tqdm(commits)):
        message = commit.message

        fix = 1.0 if (is_fix(message)) else 0.0

        feat = []
        feat.append(str(commit.hex))
        feat.append(str(fix))
        features.append(feat)
    return features

def save_features(purpose_features, path="./results/purpose_features.csv"):
    """
    Save the purpose features to a csv file.
    """
    with open(path, 'w') as csv_file:
        writer = csv.writer(csv_file)
        writer.writerow(["commit", "purpose"])
        for row in purpose_features:
            if row:
                writer.writerow([row[0], row[1]])


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
        "--branch",
        "-b",
        type=str,
        default="refs/heads/master",
        help="Which branch to use.")

    ARGS = PARSER.parse_args()
    REPOPATH = ARGS.repository
    BRANCH = ARGS.branch

    FEATURES = get_purpose_features(REPOPATH, BRANCH)
    save_features(FEATURES)
