"""
Script for extracting the experience features in a software repository.
"""
__author__ = "Oscar Svensson"
__copyright__ = "Copyright (c) 2018 Axis Communications AB"
__license__ = "MIT"

import csv
import json
import sys
import time

from argparse import ArgumentParser
from datetime import datetime
from numpy import floor
from pygit2 import Repository, GIT_SORT_TOPOLOGICAL, GIT_SORT_REVERSE
from tqdm import tqdm

def set_to_list(obj):
    """
    Helper function to turn sets to lists and floats to strings.
    """
    if isinstance(obj, set):
        return list(obj)
    if isinstance(obj, float):
        return str('%.15g' % obj)
    raise TypeError


def get_files_in_tree(tree, repo):
    """
    Function to get the files in a tree.
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
    Function to get the files that differs between two commits.
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

def save_experience_features_graph(repo_path, branch, graph_path):
    """
    Function to get and save the experience graph.
    """
    repo = Repository(repo_path)
    head = repo.references.get(branch)

    commits = list(
        repo.walk(head.target, GIT_SORT_TOPOLOGICAL | GIT_SORT_REVERSE))
    current_commit = repo.head.target

    start_time = time.time()

    current_commit = repo.get(str(current_commit))
    files = get_files_in_tree(current_commit.tree, repo)

    all_authors = {}

    author = current_commit.committer.name

    all_authors[author] = {}
    all_authors[author]['lastcommit'] = current_commit.hex
    all_authors[author][current_commit.hex] = {}
    all_authors[author][current_commit.hex]['prevcommit'] = ""
    all_authors[author][current_commit.hex]["exp"] = 1
    all_authors[author][current_commit.hex]["rexp"] = [[len(files), 1]]
    all_authors[author][current_commit.hex]["sexp"] = {}

    for i, commit in enumerate(tqdm(commits[1:])):
        files = get_diffing_files(commit, commits[i], repo)

        author = commit.committer.name
        if author not in all_authors:
            all_authors[author] = {}
            all_authors[author]['lastcommit'] = commit.hex
            all_authors[author][commit.hex] = {}
            all_authors[author][commit.hex]['prevcommit'] = ""
            all_authors[author][commit.hex]["exp"] = 1
            all_authors[author][commit.hex]["rexp"] = [[len(files), 1.0]]
            all_authors[author][commit.hex]["sexp"] = {}
        else:
            last_commit = all_authors[author]["lastcommit"]
            all_authors[author]["lastcommit"] = commit.hex
            all_authors[author][commit.hex] = {}
            all_authors[author][commit.hex]['prevcommit'] = last_commit
            all_authors[author][commit.hex][
                'exp'] = 1 + all_authors[author][last_commit]['exp']

            date_current = datetime.fromtimestamp(commit.commit_time)
            date_last = datetime.fromtimestamp(repo.get(last_commit).commit_time)

            diffing_years = abs(floor(float((date_current - date_last).days) / 365))

            overall = all_authors[author][last_commit]['rexp']

            all_authors[author][commit.hex][
                'rexp'] = [[len(files), 1.0]] + [[e[0], e[1] + diffing_years]
                                                 for e in overall]

    with open(graph_path, 'w') as output:
        json.dump(all_authors, output, default=set_to_list)

    end_time = time.time()

    print("Done")
    print("Overall processing time {}".format(end_time - start_time))

def load_experience_features_graph(path="./results/author_graph.json"):
    """
    Function to load the feeatures graph.
    """
    file_graph = {}
    with open(path, 'r') as inp:
        file_graph = json.load(inp, parse_float=lambda x: float(x))
    return file_graph


def get_experience_features(graph, repo_path, branch):
    """
    Function that extracts the experience features from a experience graph.
    """
    repo = Repository(repo_path)
    head = repo.references.get(branch)

    commits = list(
        repo.walk(head.target, GIT_SORT_TOPOLOGICAL | GIT_SORT_REVERSE))
    current_commit = repo.head.target

    files = get_files_in_tree(repo.get(str(current_commit)).tree, repo)

    features = []

    commit_feat = []
    commit_feat.append(str(commits[0].hex))
    commit_feat.append(str(1.0))
    commit_feat.append(str(len(files)))
    commit_feat.append(str(0.0))
    features.append(commit_feat)

    for _, commit in enumerate(tqdm(commits[1:])):
        author = commit.committer.name

        exp = graph[author][commit.hex]['exp']
        rexp = graph[author][commit.hex]['rexp']
        try:
            rrexp = sum([float(float(e[0]) / (float(e[1]) + 1)) for e in rexp])
        except:
            print(author)
            print(commit.hex)
            print(rexp)
            sys.exit(1)

        commit_feat = []
        commit_feat.append(str(commit.hex))
        commit_feat.append(str(float(exp)))
        commit_feat.append(str(float(rrexp)))
        commit_feat.append(str(float(0)))
        features.append(commit_feat)
    return features


def save_experience_features(history_features, path):
    """
    Save the experience features to a csv file.
    """
    with open(path, 'w') as csv_file:
        writer = csv.writer(csv_file)
        writer.writerow(["commit", "experience", "rexp", "sexp"])
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
        default="./results/author_graph.json",
        help="The path to where the graph is stored.")
    PARSER.add_argument(
        "--output",
        "-o",
        type=str,
        default="./results/experience_features.csv",
        help="The path where the output is written.")

    ARGS = PARSER.parse_args()
    REPO_PATH = ARGS.repository
    BRANCH = ARGS.branch
    SAVE_GRAPH = ARGS.save_graph
    GRAPH_PATH = ARGS.graph_path
    OUTPUT = ARGS.output

    if SAVE_GRAPH:
        save_experience_features_graph(REPO_PATH, BRANCH, GRAPH_PATH)
    GRAPH = load_experience_features_graph(GRAPH_PATH)
    EXPERIENCE_FEATURES = get_experience_features(GRAPH, REPO_PATH, BRANCH)
    save_experience_features(EXPERIENCE_FEATURES, OUTPUT)
