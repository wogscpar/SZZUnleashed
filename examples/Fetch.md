## Fetch bugs from a jira project

To fetch bugs from a Jira project one can use the the [fetch.py](../fetch_jira_bugs/fetch.py) script. As an example one can use the [Jenkins project](https://github.com/jenkinsci/jenkins) to get issues from. Their [Jira board](https://issues.jenkins-ci.org/secure/Dashboard.jspa) contains issues that could be linked to bugs.

The fetch.py script has been executed in a Unix environment and has known issues on Windows and it is therefore recommended to run this on either Linux or Mac.

So to fetch issues from Jira, execute:

```bash
python fetch.py --issue-code JENKINS --jira-project issues.jenkins-ci.org
```

The **--issue-code** argument points to which project to fetch issues from, like in this example the project [JENKINS](https://issues.jenkins-ci.org/projects/JENKINS/issues/JENKINS-56068?filter=allopenissues). In Jenkins, there are several project that can be found [here](https://issues.jenkins-ci.org/secure/BrowseProjects.jspa?selectedCategory=all&selectedProjectType=software).

The **--jira-project** argument then points to jira project url. Note that the url should provided without the https:// prefix.

The produced result are then located in *./issues*, aka the same directory as the *fetch.py* script itself where all issues are split into files of 1000 issues each. One can find some finished results in the [data directory](./data/issues).