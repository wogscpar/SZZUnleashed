"""
Script to extract code churns.
"""
__author__ = "Oscar Svensson"
__copyright__ = "Copyright (c) 2018 Axis Communications AB"
__license__ = "MIT"

import csv
import os
import sys
import time

from argparse import ArgumentParser

from multiprocessing import Process, Manager, cpu_count
from pygit2 import Repository, GIT_SORT_REVERSE, GIT_SORT_TOPOLOGICAL
from tqdm import tqdm

# Global variables
MANAGER = Manager()
RES = MANAGER.dict()


def parse_code_churns(pid, repo_path, branch, start, stop=-1):
    """
    Function that is intended to be runned by a process. It extracts the code churns
    for a set of commits and stores them in the RES dict.
    """
    repo = Repository(repo_path)

    head = repo.references.get(branch)
    commits = list(
        repo.walk(head.target, GIT_SORT_TOPOLOGICAL | GIT_SORT_REVERSE))

    start = start - 1 if (start > 0) else start
    commits = commits[start:stop] if (stop != -1) else commits[start:]

    code_churns = [[] for c in range(len(commits))]
    for i, commit in enumerate(tqdm(commits[1:], position=pid)):
        diff = repo.diff(commits[i], commit)

        tree = commit.tree
        patches = [p for p in diff]
        stats = diff.stats

        # Count the total lines of code and find the biggest file that have been changed
        total_tloc = 0
        line_of_code_old = 0
        for patch in patches:
            if patch.delta.is_binary:
                continue
            new_file = patch.delta.new_file

            # Total lines of code
            total_tloc += get_file_lines_of_code(repo, tree, new_file)

            old_file = patch.delta.old_file
            # Total lines of code in the old file
            line_of_code_old = max(
                line_of_code_old, get_file_lines_of_code(repo, tree, old_file))

        # Churned lines of code
        cloc = stats.insertions
        # Deleted lines of code
        dloc = stats.deletions

        # Churned files
        files_churned = len(patches)

        # File count
        num_files = count_files(tree, repo)

        # Apply relative code churns
        measure_one = float(cloc) / total_tloc if (total_tloc > 0) else float(cloc)
        measure_two = float(dloc) / total_tloc if (total_tloc > 0) else float(cloc)
        measure_three = (float(files_churned) / num_files if (num_files > 0)
                         else float(files_churned))

        line_of_code_old = float(line_of_code_old)

        # Churn features
        code_churns[i].append(str(commit.hex))
        code_churns[i].append(str(measure_one))
        code_churns[i].append(str(measure_two))
        code_churns[i].append(str(measure_three))
        code_churns[i].append(str(line_of_code_old))

    RES[pid] = code_churns


def count_files(tree, repo):
    """
    Count how many files there are in a repository.
    """
    num_files = 0
    trees = []
    visited = set()
    visited.add(tree.id)
    trees.append(tree)

    while trees:
        current_tree = trees.pop()
        for entry in current_tree:
            if entry.type == "tree":
                if entry.id not in visited:
                    trees.append(repo[entry.id])
                    visited.add(entry.id)
            else:
                num_files += 1
    return num_files


def get_file_lines_of_code(repo, tree, dfile):
    """
    Count how many lines of code there are in a file.
    """
    tloc = 0
    try:
        blob = repo[tree[dfile.path].id]

        tloc = len(str(blob.data).split('\\n'))
    except Exception as _:
        return tloc
    return tloc


def get_code_churns(repo_path, branch):
    """
    General function for extracting code churns. It first extracts the code churns for
    the first commit and then starts a number of processes(equal to the number of cores
    on the computer), which equally extracts the code churns for the remaining commits.
    """
    repo = Repository(repo_path)

    head = repo.references.get(branch)

    commits = list(
        repo.walk(head.target, GIT_SORT_TOPOLOGICAL | GIT_SORT_REVERSE))
    code_churns = [[]]

    initial = commits[0]

    # Relative code churns
    measure_one = 0.0
    measure_two = 0.0
    measure_three = 1.0

    line_of_code_old = 0.0

    code_churns[0].append(str(initial.hex))
    code_churns[0].append(str(measure_one))
    code_churns[0].append(str(measure_two))
    code_churns[0].append(str(measure_three))
    code_churns[0].append(str(line_of_code_old))

    # Check how many processes that could be spawned
    cpus = cpu_count()
    print("Using {} cpus...".format(cpus))

    # Equally split the commit set into the equally sized parts.
    quote, remainder = divmod(len(commits), cpus)

    processes = [
        Process(
            target=parse_code_churns,
            args=(i, repo_path, branch, i * quote + min(i, remainder),
                  (i + 1) * quote + min(i + 1, remainder))) for i in range(cpus)
    ]

    for process in processes:
        process.start()

    start_time = time.time()
    for process in processes:
        process.join()
    end_time = time.time()

    print("Done")
    print("Overall processing time {}".format(end_time - start_time))

    # Assemble the results
    churns = []
    for _, churn in RES.items():
        churns.extend(churn)

    churns = list(reversed(churns))
    churns.append(code_churns[0])
    return churns

def save_churns(churns, path="./results/code_churns_features_multithread.csv"):
    """
    Saves the code churns to a csv file.
    """
    with open(path, 'w') as csv_file:
        writer = csv.writer(csv_file)
        writer.writerow([
            "commit", "lines_of_code_added", "lines_of_code_deleted",
            "files_churned", "line_of_code_old"
        ])

        for row in churns:
            if row:
                writer.writerow([row[0], row[1], row[2], row[3], row[4]])


if __name__ == "__main__":
    PARSER = ArgumentParser(description="Utility to extract code churns from" +
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

    if not os.path.exists(REPOPATH):
        print("The repository path does not exist!")
        sys.exit(1)

    CHURNS = get_code_churns(REPOPATH, BRANCH)
    save_churns(CHURNS)
