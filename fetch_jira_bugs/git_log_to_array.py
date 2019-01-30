""" Dump the git log to a file """
__author__ = "Kristian Berg"
__copyright__ = "Copyright (c) 2018 Axis Communications AB"
__license__ = "MIT"
__credits__ = ["Kristian Berg", "Oscar Svensson"]

import argparse
import subprocess
import sys
import json

def git_log_to_json(init_hash, path_to_repo):
    hashes = subprocess.run(['git', 'rev-list', init_hash], cwd=path_to_repo,
        stdout=subprocess.PIPE).stdout.decode('ascii').split()

    logs = []
    i = 0
    for hash in hashes:
        entry = subprocess.run(['git', 'show', '--quiet', '--date=iso', hash],
            cwd=path_to_repo, stdout=subprocess.PIPE)\
            .stdout.decode(errors='replace')
        logs.append(entry)
        i += 1
        if i % 10 == 0:
            print(i, end='\r')

    with open('gitlog.json', 'w') as f:
        f.write(json.dumps(logs))

# Commits are saved in reverse chronological order from newest to oldest
if __name__ == '__main__':

    parser = argparse.ArgumentParser(description="""Convert a git log output to json.
                                                 """)
    parser.add_argument('--from-commit', type=str,
            help="A SHA-1 representing a commit. Runs git rev-list from this commit.")
    parser.add_argument('--repo-path', type=str,
            help="The absolute path to a local copy of the git repository from where the git log is taken.")

    args = parser.parse_args()
    path_to_repo = args.repo_path
    init_hash = args.from_commit
    git_log_to_json(init_hash, path_to_repo)

