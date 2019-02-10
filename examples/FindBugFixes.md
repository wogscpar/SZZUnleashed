## Find bugfixes from issues and a gitlog.json

To link bugfixes to issues one can use the [find_bug_fixes.py](../fetch_jira_bugs/find_bug_fixes.py) script. This script uses the results from the [fetch.py](../fetch_jira_bugs/fetch.py) and the [git_log_to_array.py](../fetch_jira_bugs/git_log_to_array.py) scripts. Using the results from previous examples in [Fetch](Fetch.md) and [GitLogToArray](GitLogToArray.md), one can produce the data necessary for running the [SZZ algorithm](SZZ.md).

Start by locating the *issues* directory, the one produced in the [Fetch](Fetch.md) example. This directory must contain at least one **res<0...>.json**.

Second, locate the **gitlog.json** that was produced in the [GitLogToArray](GitLogToArray.md) example. Then provide these to the **find_bug_fixes.py** script.

```bash
python find_bug_fixes.py --gitlog <path_to_gitlog>/gitlog.json --issue-list <path_to_issues>/issues
```

In this example, one can use the results in [examples/data](./data) directory.

Finally, when the script is done the results are found in the **issue_list.json**. An example of that file is found on [issue_list.json](./data/issue_list.json).