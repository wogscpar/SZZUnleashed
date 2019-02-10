## Generate bug fix and introducing pairs

With a list of bug fixing commits it is possible to generate possible candidates for bug introducing commits. With the results from the example in [FindBugFixes](FindBugFixes.md), one can generate a set of pairs where the first in each pair is the fixing commits and the second is the bug introducing commit.

Start with locating the **issue_list.json** that was produced in the example [FindBugFixes](FindBugFixes.md). Then locate the local copy of the git repository that was cloned in the example [GitLogToArray](GitLogToArray.md). The two are the only requirements for the bug introducing finder to run.

In the [examples/data](./data) directory, one can find an already built jar file of the [szz_find_bug_introducers-0.1.jar](./data/jars/szz_find_bug_introducers-0.1.jar).

```bash
java -jar szz_find_bug_introducers-0.1.jar -i <path_to_issue_list.json> -r <path_to_local_git_repository>
```

The results can be found in [examples/data/fix_and_introducers_pairs.json](./data/fix_and_introducers_pairs.json).

### Optional arguments for the szz_find_bug_introducers-0.1.jar

The bug introducing commit finder can be configured to search for a broader span of commits. It uses line mapping graphs for searching for potential commits. The default depth is 3 which means that it recursively searches for commits that have changed a line at a maximum of three commits away. To change this, use the **-d** argument.

```bash
java -jar szz_find_bug_introducers-0.1.jar -i <path_to_issue_list.json> -r <path_to_local_git_repository> -d 3
```

To speed up the execution of the finder, one can specify how many cores the algorithm should use at the same time. By default it tries to use all cores that are available on the hosting system. But if one for any reason doesn't want it to use all cores, one can set the number of cores with the **-c** argument.

```bash
java -jar szz_find_bug_introducers-0.1.jar -i <path_to_issue_list.json> -r <path_to_local_git_repository> -c 1
```

By default, the finder uses the technique where it picks all commits that have made changes to a line but where it takes into consideration if they have been made before or after the bug was reported. If one wants to try the more experimental method where it uses a distance measurement, which basically is a measurement of how much a line has changed between two commits one can use the **-b** argument.

```bash
java -jar szz_find_bug_introducers-0.1.jar -i <path_to_issue_list.json> -r <path_to_local_git_repository> -b distance
```

It is possible to modify how many lines a diff should contain when diffing a file between two commits. Like in git, a diff can show additional lines when diffing two commits. That means not just the changed lines but also lines that surrounds the changed lines. This could be useful if one needs to implement a diff on function level and not just row level.

```bash
java -jar szz_find_bug_introducers-0.1.jar -i <path_to_issue_list.json> -r <path_to_local_git_repository> -dc 2
```