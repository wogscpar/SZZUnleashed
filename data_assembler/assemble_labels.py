"""
Script to generate a labels file from a file produced by the SZZ algorithm.
"""
__author__ = "Oscar Svensson"
__copyright__ = "Copyright (c) 2018 Axis Communications AB"
__license__ = "MIT"

import csv
import json

from argparse import ArgumentParser
from datetime import datetime as dat
from pygit2 import Repository, GIT_SORT_TOPOLOGICAL, GIT_SORT_REVERSE
from tqdm import tqdm

import matplotlib.pyplot as plt


def get_labels(repo_path, branch, pair_file, last_commit):
    """
    Get the labels from a file produced by the SZZ algorithm. It contains
    bug fixing commits and their respective bug fixing commit.
    """
    repo = Repository(repo_path)
    head = repo.references.get(branch)

    commits = []
    for commit in list(
            repo.walk(head.target, GIT_SORT_TOPOLOGICAL | GIT_SORT_REVERSE)):
        commits.append(commit)
        if commit.hex == last_commit:
            break

    commits = list(reversed(commits))

    pairs = {}
    with open(pair_file, 'r') as inp:
        pairs = json.load(inp)

    unique_pairs = set([p[1] for p in pairs])
    labels = []

    for commit in tqdm(commits):
        label = [commit.hex, "1" if commit.hex in unique_pairs else "0"]
        labels.append(label)

    return labels


def save_labels(labels, res_path):
    """
    Save the labels as a csv file.
    """
    with open(res_path, 'w') as out:
        writer = csv.writer(out)
        writer.writerow(["commit", "label"])
        for label in labels:
            writer.writerow(label)


def save_label_distribution(repo_path, branch, labels, res_path):
    """
    Save a distribution of the labels over time.
    """
    ldict = set()
    for label in labels:
        if label[1] == "1":
            ldict.add(label[0])

    repo = Repository(repo_path)
    head = repo.references.get(branch)

    commits = list(repo.walk(head.target, GIT_SORT_TOPOLOGICAL))

    start_year = dat.fromtimestamp(commits[-1].commit_time).year
    end_year = dat.fromtimestamp(commits[0].commit_time).year

    num_years = end_year - start_year
    year_dist = [0 for y in range(num_years + 1)]
    years = [y for y in range(start_year, end_year + 1)]

    for commit in commits:
        if commit.hex in ldict:
            commit_year = dat.fromtimestamp(commit.commit_time).year
            year_dist[commit_year - start_year - 1] += 1

    fig = plt.figure()
    plt.bar(years, year_dist)
    plt.xticks(years)
    plt.xlim(xmin=years[0] - 1, xmax=years[-1] + 1)
    fig.autofmt_xdate()
    plt.savefig(res_path)


if __name__ == "__main__":
    PARSER = ArgumentParser(
        description="Utility to extract unique bug " +
        "introducing commits from a set a bug fix and bug introducing pairs.")
    PARSER.add_argument(
        "--repository",
        "-r",
        type=str,
        default="../../jenkins_master/jenkins_master",
        help=
        "Path to a local git repository from which the pairs where extracted.")
    PARSER.add_argument(
        "--branch",
        "-b",
        type=str,
        default="refs/heads/master",
        help="Which branch to use.")
    PARSER.add_argument(
        "--file",
        "-f",
        type=str,
        default="../szz/results/fix_and_introducers_pairs.json",
        help="The file with the pairs.")
    PARSER.add_argument(
        "--resfile",
        "-rf",
        type=str,
        default="./labels.csv",
        help="The file to which the labels are written.")
    PARSER.add_argument(
        "--figfile",
        "-ff",
        type=str,
        default="./distribution.png",
        help="The file to which the bug introducing ditribution is written.")
    PARSER.add_argument(
        "--commit",
        "-c",
        type=str,
        default="02d6908ada70fcf8012833ddef628bc09c6f8389",
        help="The last commit that should be analyzed.")

    ARGS = PARSER.parse_args()
    REPOPATH = ARGS.repository
    BRANCH = ARGS.branch
    PAIRFILE = ARGS.file
    RESFILE = ARGS.resfile
    FIGFILE = ARGS.figfile
    LAST_COMMIT = ARGS.commit

    LABELS = get_labels(REPOPATH, BRANCH, PAIRFILE, LAST_COMMIT)

    save_labels(LABELS, RESFILE)

    save_label_distribution(REPOPATH, BRANCH, LABELS, FIGFILE)
