""" Identify bugfixes in Jenkins repository given a list of issues """
__author__ = "Kristian Berg"
__copyright__ = "Copyright (c) 2018 Axis Communications AB"
__license__ = "MIT"

import os
import json
import re
import argparse

def find_bug_fixes(issue_path, gitlog_path, gitlog_pattern):
    """ Identify bugfixes in Jenkins repository given a list of issues """

    i = 0 # Used to display progress
    no_matches = []
    matches_per_issue = {}
    total_matches = 0

    issue_list = build_issue_list(issue_path)
    with open(gitlog_path) as f:
        gitlog = json.loads(f.read())

    for key in issue_list:
        nbr = key.split('-')[1]
        matches = []

        for commit in gitlog:
            pattern = gitlog_pattern.format(nbr=nbr)
            if re.search(pattern, commit):
                if re.search(r'#{nbr}\D'.format(nbr=nbr), commit) \
                    and not re.search('[Ff]ix', commit):
                    pass
                else:
                    matches.append(commit)
        total_matches += len(matches)
        matches_per_issue[key] = len(matches)

        if matches:
            selected_commit = commit_selector_heuristic(matches)
            if not selected_commit:
                no_matches.append(key)
            else:
                issue_list[key]['hash'] = \
                    re.search('(?<=^commit )[a-z0-9]+(?=\n)', \
                    selected_commit).group(0)
                issue_list[key]['commitdate'] = \
                    re.search('(?<=\nDate:   )[0-9 -:+]+(?=\n)',\
                    selected_commit).group(0)
        else:
            no_matches.append(key)

        # Progress counter
        i += 1
        if i % 10 == 0:
            print(i, end='\r')

    print('Total issues: ' + str(len(issue_list)))
    print('Issues matched to a bugfix: ' + str(len(issue_list) - len(no_matches)))
    print('Percent of issues matched to a bugfix: ' + \
          str((len(issue_list) - len(no_matches)) / len(issue_list)))
    for key in no_matches:
        issue_list.pop(key)

    return issue_list


def build_issue_list(path):
    """ Helper method for find_bug_fixes """
    issue_list = {}
    for filename in os.listdir(path):
        with open(path + '/' + filename) as f:
            for issue in json.loads(f.read())['issues']:
                issue_list[issue['key']] = {}

                created_date = issue['fields']['created'].replace('T', ' ')
                created_date = created_date.replace('.000', ' ')
                issue_list[issue['key']]['creationdate'] = created_date

                res_date = issue['fields']['resolutiondate'].replace('T', ' ')
                res_date = res_date.replace('.000', ' ')
                issue_list[issue['key']]['resolutiondate'] = res_date
    return issue_list

def commit_selector_heuristic(commits):
    """ Helper method for find_bug_fixes.
    Commits are assumed to be ordered in reverse chronological order.
    Given said order, pick first commit that does not match the pattern.
    If all commits match, return newest one. """
    for commit in commits:
        if not re.search('[Mm]erge|[Cc]herry|[Nn]oting', commit):
            return commit
    return commits[0]

def main():
    """ Main method """
    parser = argparse.ArgumentParser(description="""Identify bugfixes. Use this script together with a
                                                    gitlog.json and a path with issues. The gitlog.json
                                                    is created using the git_log_to_array.py script and
                                                    the issue directory is created and populated using
                                                    the fetch.py script.""")
    parser.add_argument('--gitlog', type=str,
                        help='Path to json file containing gitlog')
    parser.add_argument('--issue-list', type=str,
                        help='Path to directory containing issue json files')
    parser.add_argument('--gitlog-pattern', type=str,
                        help='Pattern to match a bugfix')
    args = parser.parse_args()

    issue_list = find_bug_fixes(args.issue_list, args.gitlog, args.gitlog_pattern)
    with open('issue_list.json', 'w') as f:
        f.write(json.dumps(issue_list))

if __name__ == '__main__':
    main()
