## Convert a gitlog to an array

To convert a gitlog to an array of commits, one can use the [git_log_to_array.py](../fetch_jira_bugs/git_log_to_array.py) script. It requires a local copy of the git repository that is aimed to be analyzed. As an example one can use the [Jenkins repository](https://github.com/jenkinsci/jenkins).

First clone the repository to a location of your own choice.

```bash
git clone https://github.com/jenkinsci/jenkins
```

Then decide from which commit the script should produce an array from. In this example the following commit was chosen, [02d6908ada70fcf8012833ddef628bc09c6f8389](https://github.com/jenkinsci/jenkins/commit/02d6908ada70fcf8012833ddef628bc09c6f8389). Then run the script using:

```bash
python git_log_to_array.py --repo-path <path_to_cloned_repo>/jenkins --from-commit 02d6908ada70fcf8012833ddef628bc09c6f8389
```

The results from the script will be assembled in the file *gitlog.json*. An example of this run can be found in [data/gitlog.json](./data/gitlog.json).