"""
Script for extracting diffusion features from a git repository.
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
from numpy import log2
from pygit2 import Repository, GIT_SORT_TOPOLOGICAL, GIT_SORT_REVERSE
from tqdm import tqdm

MANAGER = Manager()
RES = MANAGER.dict()


def count_diffing_subsystems(subsystems):
    """
    Function for counting the number of subsystems in a repository.
    """
    number = 0
    for system in subsystems.values():
        number = number + count_diffing_subsystems(system)

    return number + len(subsystems.keys())

def count_entropy(file_changes, total_change):
    """
    Function to count entropy for some file changes.
    """
    if total_change == 0:
        return 0
    return sum([
        -1 * (float(x) / total_change) * (log2(float(x) / total_change)
                                          if x > 0 else 0)
        for x in file_changes
    ])


def parse_diffusion_features(pid, repo_path, branch, start, stop=-1):
    """
    Function to extract diffusion features from a set of commits.
    """
    repo = Repository(repo_path)

    head = repo.references.get(branch)
    commits = list(
        repo.walk(head.target, GIT_SORT_TOPOLOGICAL | GIT_SORT_REVERSE))

    start = start - 1 if (start > 0) else start
    commits = commits[start:stop] if (stop != -1) else commits[start:]

    features = [[] for c in range(len(commits))]
    for i, commit in enumerate(tqdm(commits[1:], position=pid)):
        diff = repo.diff(commits[i], commit)

        patches = [p for p in diff]

        # Extract all different subsystems that have been modified
        modules = set([])
        subsystems_mapping = {}
        entropy_change = 0

        file_changes = []
        total_change = 0
        for patch in patches:
            # Skip binary files
            if patch.delta.is_binary:
                continue
            _, addition, deletions = patch.line_stats
            total_change = total_change + (addition + deletions)
            file_changes.append(addition + deletions)

            # Store all subsystems
            fpath = patch.delta.new_file.path
            subsystems = fpath.split('/')[:-1]

            root = subsystems_mapping
            for system in subsystems:
                if system not in root:
                    root[system] = {}
                root = root[system]
            if subsystems > 0:
                modules.add(subsystems[0])

        # Check how many subsystems that have been touched
        modified_systems = count_diffing_subsystems(subsystems_mapping)

        # Calculate the entropy for the commit
        entropy_change = count_entropy(file_changes, total_change)

        # Add all features
        features[i].append(str(commit.hex))
        features[i].append(str(float(modified_systems)))
        features[i].append(str(float(len(modules))))
        features[i].append(str(float(entropy_change)))

    RES[pid] = features

def parse_tree(tree, repo):
    """
    Parse a git tree and get the number of files, the number of systems and
    the number of subdirectories.
    """
    found_sub_entries = 0
    additions = 0
    file_additions = []
    tree = repo[tree.id]

    for entry in tree:
        if entry.type == "bin":
            continue
        if entry.type == "tree":
            sub_additions, sub_file_additions, sub_entries = parse_tree(
                entry, repo)
            found_sub_entries += (1 + sub_entries)
            additions += sub_additions
            file_additions.extend(sub_file_additions)
        else:
            try:
                sub_addition = len(str(repo[entry.id]).split('\n'))
                additions += sub_addition
                file_additions.append(sub_addition)
            except Exception as ex:
                print(ex)
                continue

    return additions, file_additions, found_sub_entries

def get_diffusion_features(repo_path, branch):
    """
    Function that extracts the first commits diffusion features. It then starts
    a number of processes(equal to the number of cores on the computer), and then
    distributes the remaining commits to them.
    """
    repo = Repository(repo_path)

    head = repo.references.get(branch)

    commits = list(
        repo.walk(head.target, GIT_SORT_TOPOLOGICAL | GIT_SORT_REVERSE))
    initial = commits[0]
    init_tree = initial.tree

    # Count inital total lines of code
    init_total_additions = 0
    init_file_addtions = []

    init_subdirectories = 0
    init_modules = 0

    for entry in init_tree:
        if entry.type == "tree":
            added, file_additions, subdirectories = parse_tree(entry, repo)

            init_modules += 1
            init_file_addtions.extend(file_additions)
            init_total_additions += added
            init_subdirectories += subdirectories
        else:
            try:
                additions = len(str(repo[entry.id]).split('\n'))
                init_total_additions += additions
                init_file_addtions.append(additions)
            except:
                continue
    diffusion_features = []
    diffusion_features.append(initial.hex)
    diffusion_features.append(init_subdirectories)
    diffusion_features.append(init_modules)
    diffusion_features.append(
        count_entropy(init_file_addtions, init_total_additions))

    # Check how many processes that could be spawned
    cpus = cpu_count()
    print("Using {} cpus...".format(cpus))
    # Divide the commits eqaully between the processes.
    quote, remainder = divmod(len(commits), cpus)

    processes = [
        Process(
            target=parse_diffusion_features,
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
    features = []
    for _, feat in RES.items():
        features.extend(feat)
    features = list(reversed(features))
    features.append(diffusion_features)
    return features

def save_diffusion_features(diffusion_features,
                            path="./results/diffusion_features.csv"):
    """
    Save the diffusion features to a csv file.
    """
    with open(path, 'w') as csv_file:
        writer = csv.writer(csv_file)
        writer.writerow([
            "commit", "modified_subsystems", "modified_subdirectories",
            "entropy"
        ])
        for row in diffusion_features:
            if row:
                writer.writerow([row[0], row[1], row[2], row[3]])


if __name__ == "__main__":
    PARSER = ArgumentParser(
        description="Utility to extract diffusion features from" +
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

    DIFFUSION_FEATURES = get_diffusion_features(REPOPATH, BRANCH)
    save_diffusion_features(DIFFUSION_FEATURES)
