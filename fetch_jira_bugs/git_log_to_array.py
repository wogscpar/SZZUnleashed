""" Dump the git log to a file """
__author__ = "Kristian Berg"
__copyright__ = "Copyright (c) 2018 Axis Communications AB"
__license__ = "MIT"

import subprocess
import sys
import json

# Commits are saved in reverse chronological order from newest to oldest
if __name__ == '__main__':
    path_to_repo = sys.argv[1]

    hashes = subprocess.run(['git', 'rev-list', '02d6908ada70fcf8012833ddef628bc09c6f8389'], cwd=path_to_repo,
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
