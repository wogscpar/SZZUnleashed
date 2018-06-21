"""
Script to extract history features from a git repository.
"""
__author__ = "Oscar Svensson"
__copyright__ = "Copyright (c) 2018 Axis Communications AB"
__license__ = "MIT"

import csv
import json
import time

from argparse import ArgumentParser
from pygit2 import Repository, GIT_SORT_TOPOLOGICAL, GIT_SORT_REVERSE
from tqdm import tqdm

def set_to_list(obj):
    """
    Helper function to convert a set to a list.
    """
    if isinstance(obj, set):
        return list(obj)
    raise TypeError

def get_files_in_tree(tree, repo):
    """
    Extract the hex of all files and their name.
    """
    files = set()
    for entry in tree:
        if entry.type == "tree":
            sub_files = [(f[0], "{}/{}".format(entry.name, f[1]))
                         for f in get_files_in_tree(repo[entry.id], repo)]
            files.update(sub_files)
        else:
            blob = repo[entry.id]
            if not blob.is_binary:
                if entry.name.endswith("java"):
                    files.add((entry.hex, entry.name))
    return files


def get_diffing_files(commit, parent, repo):
    """
    Get the files that diffed between two commits.
    """
    diff = repo.diff(parent, commit)

    patches = [p for p in diff]

    files = set()

    for patch in patches:
        if patch.delta.is_binary:
            continue
        nfile = patch.delta.new_file
        files.add((nfile.id, nfile.path, patch.delta.status))

    return files


def save_history_features_graph(repo_path, branch, graph_path):
    """
    Track the number of developers that have worked in a repository and save the
    results in a graph which could be used for later use.
    """
    repo = Repository(repo_path)
    head = repo.references.get(branch)

    commits = list(
        repo.walk(head.target, GIT_SORT_TOPOLOGICAL | GIT_SORT_REVERSE))
    current_commit = repo.head.target

    start_time = time.time()

    all_files = {}
    current_commit = repo.get(str(current_commit))
    files = get_files_in_tree(current_commit.tree, repo)

    for (_, name) in tqdm(files):
        all_files[name] = {}
        all_files[name]['lastcommit'] = current_commit.hex
        all_files[name][current_commit.hex] = {}
        all_files[name][current_commit.hex]["prevcommit"] = ""
        all_files[name][current_commit.hex]["authors"] = [
            current_commit.committer.name
        ]

    for i, commit in enumerate(tqdm(commits[1:])):
        files = get_diffing_files(commit, commits[i], repo)
        for (_, name, _) in files:
            if name not in all_files:
                all_files[name] = {}

            last_commit = ""
            if 'lastcommit' not in all_files[name]:
                all_files[name]['lastcommit'] = commit.hex
            else:
                last_commit = all_files[name]['lastcommit']

            all_files[name][commit.hex] = {}
            all_files[name][commit.hex]["prevcommit"] = last_commit

            authors = set([commit.committer.name])
            if last_commit:
                authors.update(all_files[name][last_commit]["authors"])
            all_files[name][commit.hex]["authors"] = authors

            all_files[name]['lastcommit'] = commit.hex

    with open(graph_path, 'w') as output:
        json.dump(all_files, output, default=set_to_list)

    end_time = time.time()

    print("Done")
    print("Overall processing time {}".format(end_time - start_time))

def load_history_features_graph(path):
    """
    Save the history features to a csv file.
    """
    file_graph = {}
    with open(path, 'r') as inp:
        file_graph = json.load(inp)
    return file_graph


def get_history_features(graph, repo_path, branch):
    """
    Function that extracts the history features from a git repository.
    They are the total number of authors, the total age and the total
    number of unique changes.
    """
    repo = Repository(repo_path)
    head = repo.references.get(branch)

    commits = list(
        repo.walk(head.target, GIT_SORT_TOPOLOGICAL | GIT_SORT_REVERSE))

    features = []

    commit_feat = []
    commit_feat.append(str(commits[0].hex))
    commit_feat.append(str(1.0))
    commit_feat.append(str(0.0))
    commit_feat.append(str(0.0))
    features.append(commit_feat)

    for i, commit in enumerate(tqdm(commits[1:])):
        files = get_diffing_files(commit, commits[i], repo)

        total_number_of_authors = set()
        total_age = []
        total_unique_changes = set()

        for (_, name, _) in files:
            sub_graph = graph[name][commit.hex]
            total_number_of_authors.update(sub_graph['authors'])

            prev_commit = sub_graph['prevcommit']
            if prev_commit:
                total_unique_changes.add(prev_commit)

                prev_commit_obj = repo.get(prev_commit)

                total_age.append(commit.commit_time -
                                 prev_commit_obj.commit_time)

        total_age = float(sum(total_age)) / len(total_age) if total_age else 0

        commit_feat = []
        commit_feat.append(str(commit.hex))
        commit_feat.append(str(float(len(total_number_of_authors))))
        commit_feat.append(str(float(total_age)))
        commit_feat.append(str(float(len(total_unique_changes))))
        features.append(commit_feat)
    return features


def save_history_features(history_features, path):
    """
    Function to save the history features as a csv file.
    """
    with open(path, 'w') as csv_file:
        writer = csv.writer(csv_file)
        writer.writerow(
            ["commit", "number_of_authors", "age", "number_unique_changes"])
        for row in history_features:
            if row:
                writer.writerow([row[0], row[1], row[2], row[3]])


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
    PARSER.add_argument(
        "--save-graph",
        "-sg",
        action="store_true",
        help="Generate a new graph for a repository.")
    PARSER.add_argument(
        "--graph-path",
        "-gp",
        type=str,
        default="./results/file_graph.json",
        help="The path to where the graph is stored.")
    PARSER.add_argument(
        "--output",
        "-o",
        type=str,
        default="./results/history_features.csv",
        help="The path where the output is written.")

    ARGS = PARSER.parse_args()
    REPO_PATH = ARGS.repository
    BRANCH = ARGS.branch
    SAVE_GRAPH = ARGS.save_graph
    GRAPH_PATH = ARGS.graph_path
    OUTPUT = ARGS.output
    print(SAVE_GRAPH)

    if SAVE_GRAPH:
        save_history_features_graph(REPO_PATH, BRANCH, GRAPH_PATH)
    GRAPH = load_history_features_graph(GRAPH_PATH)
    HISTORY_FEATURES = get_history_features(GRAPH, REPO_PATH, BRANCH)
    save_history_features(HISTORY_FEATURES, OUTPUT)
