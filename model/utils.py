"""Returns date of specific commit given a hash
OR date of first commit result given a command"""
__author__ = "Kristian Berg"
__copyright__ = "Copyright (c) 2018 Axis Communications AB"
__license__ = "MIT"

from datetime import datetime
import subprocess
import re

def datetime_of_commit(path, hashval=None, command=None):
    """Returns date of specific commit given a hash
    OR date of first commit result given a command"""
    # Check that either hash or command parameter has a value
    if hashval:
        command = ['git', 'show', '--quiet', '--date=iso', hashval]
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
