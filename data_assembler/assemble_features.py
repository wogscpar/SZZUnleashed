"""
Script that runs several docker containers which in turn runs an analysis on
a git repository.
"""
__author__ = "Oscar Svensson"
__copyright__ = "Copyright (c) 2018 Axis Communications AB"
__license__ = "MIT"

import os
import sys
import shutil
import time

from argparse import ArgumentParser
from distutils.dir_util import copy_tree
from multiprocessing import Process, cpu_count
from git import Repo
from tqdm import tqdm

import docker

def start_container(client, image, name, repo_dir, result_dir):
    """
    Function that starts a docker container and links the repo into it and
    a directory where the results are stored.
    """
    for container in client.containers.list(all=True):
        if name == container.name:
            if container.status == "running":
                container.kill()
            container.remove()

    path = os.path.abspath('./')

    container = client.containers.run(
        image,
        name=name,
        stdin_open=True,
        detach=True,
        volumes={
            str(path + "/scripts"): {
                'bind': '/root/scripts',
                'mode': 'rw'
            },
            result_dir: {
                'bind': '/root/results',
                'mode': 'rw'
            },
            os.path.abspath(repo_dir): {
                'bind': '/root/repo',
                'mode': 'rw'
            }
        },
        command="bash")

    return container

def run_command(container, command):
    """
    Function that executes a command inside a container.
    """
    return container.exec_run(
        cmd="bash -c \"" + command + "\"", tty=True, privileged=True)


def run_analysis(t_id, container, commits):
    """
    Function that runs a command inside all docker container.
    """
    for commit in tqdm(
            commits, desc="Progress process {}".format(t_id), position=t_id):
        run_command(container,
                    "/root/scripts/analyse_commit {}".format(commit))

def copy_repo(src, dest):
    """
    Helper function to copy a repository to another destination.
    """
    try:
        shutil.copytree(src, dest)
    except shutil.Error as exp:
        print("Directory not copied. Error: {}".format(exp))
    except OSError as exp:
        print("Directory not copied. Error: {}".format(exp))

def partion_commits(commits, partitions):
    """
    Function that divides commits into evenly partitions.
    """
    quote, remainder = divmod(len(commits), partitions)
    chunk_commits = [(i * quote + min(i, remainder), (i + 1) * quote + min(i + 1, remainder) - 1)
                     for i in range(partitions)]
    chunk_commits[-1] = (chunk_commits[-1][0], chunk_commits[-1][1] + 1)

    commits = [[commit for commit in commits[chunk[0]:chunk[1]]]
               for chunk in chunk_commits]
    return commits

def start_analysis(image, result_dir, commits=None, cpus=cpu_count()):
    """
    This function starts a docker container that can analyze a git repository. It starts several
    containers if the cpus are more than one.
    """
    client = docker.from_env()
    repo = Repo(REPO)

    # Since the script is working directly on the repository, they have
    # to have a separately copy.
    if not os.path.exists("./repos"):
        os.makedirs("./repos")

    repo_name = os.path.basename(os.path.normpath(REPO))

    for cpu in range(cpus):
        copy_repo(REPO, "./repos/{}{}".format(repo_name, cpu))

    # Split the commits into even parts.
    if not commits:
        commits = [
            str(commit.hexsha) for commit in list(repo.iter_commits('master'))
        ]

    commits = partion_commits(commits, cpus)

    containers = []
    for cpu in range(cpus):
        container = start_container(
            client,
            image=image,
            name="analysis_{}_cpu_{}".format(repo_name, cpu),
            repo_dir="./repos/{}{}".format(repo_name, cpu),
            result_dir=result_dir + "/data{}".format(cpu))
        containers.append(container)

    processes = [
        Process(target=run_analysis, args=(i, containers[i], commits[i]))
        for i in range(cpus)
    ]
    for process in processes:
        process.start()
    for process in processes:
        process.join()

    for container in containers:
        print(container.status)
        print(container.name)
        if (container.status != "exited" or container.status != "dead"):
            container.kill()
        container.remove()

    shutil.rmtree("./repos", ignore_errors=True)

def parse_commits(commit_file):
    """
    Read the commits from a file and reutrn the content.
    """
    if not os.path.exists(commit_file):
        print("commit_file doesn't exist!!", file=sys.stderr)
        sys.exit(1)

    commits = []
    with open(commit_file, 'r') as cfile:
        commits = [line.strip() for line in cfile.readlines()]
    return commits

def assemble_directories(result_path, cpus=cpu_count()):
    """
    Copy all results into a single directory.
    """
    result_path = os.path.abspath(result_path)
    paths = ["{}/data{}".format(result_path, i) for i in range(cpus)]

    if not all([os.path.exists(p) for p in paths]):
        print("data paths doesn't exists!", file=sys.stderr)
        return

    files = []

    for path in paths:
        for item in os.listdir(path):
            commit = os.path.join(path, item)
            corrupt = False if (len(os.listdir(commit)) == 2) else True

            if (os.path.isdir(commit) and not corrupt):
                files.append((commit, item))

    print("Saving all analysed commits into a single directory: {}/data_all".
          format(result_path))
    if not os.path.exists("{}/data_all".format(result_path)):
        os.makedirs("{}/data_all".format(result_path))

    for file_tuple in files:
        if not os.path.exists("{}/data_all/{}".format(result_path, file_tuple[1])):
            copy_tree(file_tuple[0], "{}/data_all/{}".format(result_path, file_tuple[1]))

def check_for_missing_commits(repo_path, result_path):
    """
    Controller function that checks if all commits has been analyzed.
    """
    result_dir = os.path.abspath(result_path)
    if not os.path.exists(result_path):
        print("Result path doesn't exist!", file=sys.stderr)
        return

    repo = Repo(repo_path)

    current_commits = []
    for item in os.listdir(result_dir):
        current_commits.append(item)

    all_repo_commits = [c.hexsha for c in list(repo.iter_commits('master'))]

    missing_commits = set(all_repo_commits) - set(current_commits)

    if missing_commits:
        with open("./missing_commits.txt", 'w') as cfile:
            for commit in missing_commits:
                cfile.write(commit)
                cfile.write('\n')
        print("Wrote missing commits to missing_commits.txt")

if __name__ == "__main__":
    PARSER = ArgumentParser(description="Utility to run several docker " +
                            "containers onto a git repository. " +
                            "Each container is given a set of " +
                            "commits and is instructed to run " +
                            "an analysis on each one of them.")
    PARSER.add_argument(
        "--analyse", "-a", action="store_true", help="Run an analysation.")
    PARSER.add_argument(
        "--image",
        "-i",
        type=str,
        default="code-maat",
        help="Specification of which image to use.")
    PARSER.add_argument(
        "--repo-dir",
        "-r",
        type=str,
        default="../../jenkins",
        help="Specification of which repo to use.")
    PARSER.add_argument(
        "--result-dir",
        "-rd",
        type=str,
        default="/h/oskars",
        help="Specification of where to store the result.")
    PARSER.add_argument(
        "--commits",
        "-c",
        type=str,
        default=None,
        help="Direction to a file containing commits to analyse.")
    PARSER.add_argument(
        "--assemble",
        "-as",
        action="store_true",
        help="Assemble the results into a single directory.")
    PARSER.add_argument(
        "--missing-commits",
        "-mc",
        action="store_true",
        help="Check for non analysed commits.")

    ARGS = PARSER.parse_args()

    global REPO
    REPO = os.path.abspath(ARGS.repo_dir)

    if ARGS.commits:
        COMMITS = parse_commits(ARGS.commits)
    else:
        COMMITS = []

    CLIENT = docker.from_env()
    if ARGS.analyse:
        print("Starting the analysis using {} cpus...".format(cpu_count()))
        START = time.time()
        if COMMITS:
            start_analysis(ARGS.image, ARGS.result_dir, commits=COMMITS)
        else:
            start_analysis(ARGS.image, ARGS.result_dir)
        STOP = time.time()
        print("Done in {}".format(
            time.strftime('%H:%M:%S', time.gmtime(STOP - START))))
        print("Results can be found in {}".format(
            ARGS.result_dir + "/data{" +
            ','.join(["{}".format(i) for i in range(cpu_count())]) + "}"))
    if ARGS.assemble:
        assemble_directories(ARGS.result_dir)
    if ARGS.missing_commits:
        check_for_missing_commits(ARGS.repo_dir, ARGS.result_dir)
