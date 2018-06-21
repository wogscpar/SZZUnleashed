""" Generate train and test set. """
__author__ = "Kristian Berg"
__copyright__ = "Copyright (c) 2018 Axis Communications AB"
__license__ = "MIT"

import subprocess
import re
import json
from datetime import datetime, timedelta

# TODO: give update parameter as fraction
def build_sets(path, sgap=timedelta(days=200), gap=timedelta(days=150),
               egap=timedelta(days=150), update=timedelta(days=400),
               testdur=timedelta(days=70), traindur=timedelta(days=2000)):
    # Determine date of oldest commit in repository
    command = ['git', 'log', '--reverse', '--date=iso']
    startdate = datetime_of_commit(path, command=command)

    # Determine date of newest commit in repository
    command = ['git', 'log', '--date=iso']
    enddate = datetime_of_commit(path, command=command)

    # Add start and end gaps
    startdate += sgap
    enddate -= egap

    # Print stuff
    print('Start: ' + str(startdate))
    print('End: ' + str(enddate))
    print('Duration: ' + str(enddate - startdate))
    print('len(training) len(testing)')

    # Build list of commit hashes from oldest to newest
    command = ['git', 'rev-list', '--reverse', 'HEAD']
    res = subprocess.run(command, cwd=path, stdout=subprocess.PIPE)
    gitrevlist = res.stdout.decode('utf-8')
    hashes = gitrevlist.split()

    # Initiate loop variables
    trainsets = []
    testsets = []
    training = []
    testing = []
    train_index = 0
    test_index = 0
    tsplit = startdate + traindur

    # Adjust start index to correspond to start date
    commitdate = datetime_of_commit(path, hash=hashes[train_index])
    while commitdate < startdate:
        train_index += 1
        commitdate = datetime_of_commit(path, hash=hashes[train_index])

    # TODO: Last few commits are not used
    while tsplit + gap + testdur < enddate:
        # Set test index to correspond to appropriate date
        test_index = train_index
        commitdate = datetime_of_commit(path, hash=hashes[test_index])
        while commitdate < tsplit + gap:
            test_index += 1
            commitdate = datetime_of_commit(path, hash=hashes[test_index])

        # Build training set
        commitdate = datetime_of_commit(path, hash=hashes[train_index])
        while commitdate < tsplit:
            training.append(hashes[train_index])
            train_index += 1
            commitdate = datetime_of_commit(path, hash=hashes[train_index])
        trainsets.append(list(training))

        # Build test set
        testing = []
        commitdate = datetime_of_commit(path, hash=hashes[test_index])
        while commitdate < tsplit + gap + testdur:
            testing.append(hashes[test_index])
            test_index += 1
            commitdate = datetime_of_commit(path, hash=hashes[test_index])
        testsets.append(list(testing))

        # Print stuff
        print(str(len(training)) + ' ' + str(len(testing)))

        # Loop update
        tsplit += update

    # Write results to file
    with open('trainsets.json', 'w') as f:
        f.write(json.dumps(trainsets))
    with open('testsets.json', 'w') as f:
        f.write(json.dumps(testsets))

# Returns date of specific commit given a hash
# OR date of first commit result given a command
def datetime_of_commit(path, hash=None, command=None):
    # Check that either hash or command parameter has a value
    if hash:
        command = ['git', 'show', '--quiet', '--date=iso', hash]
    elif command:
        if command[0] != 'git':
            raise ValueError('Not a git command')
        elif '--date=iso' not in command:
            raise ValueError('Command needs to specify --date=iso')
    else:
        raise ValueError('Either hash or command parameter is needed')

    # Get date of commit
    res = subprocess.run(command, cwd=path, stdout=subprocess.PIPE)
    gitlog = res.stdout.decode('utf-8', errors='ignore')
    match = re.search('(?<=\nDate:   )[0-9-+: ]+(?=\n)', gitlog).group(0)
    date = datetime.strptime(match, '%Y-%m-%d %H:%M:%S %z')
    return date

if __name__ == '__main__':
    build_sets('/home/kristiab/Git/jenkins')
