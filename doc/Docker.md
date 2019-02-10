### How to generate fix and bug introducing pairs using Docker

Docker is a great tool if one is using a system where either the Python or Java installation is not working properly. It requires docker to be installed which is something that won't be covered here but can be found in dockers own [installation instructions](https://docs.docker.com/install/). To get further information about what docker is, read the overall  [documentation](https://docs.docker.com/).

In this project it exists a file called *Dockerfile*. With this file it is possible to build a docker image that contains all the necessary parts for the algorithm to run. A docker image is more or less a template for a future virtual machine which will run a certain system, in this case a small system called Alpine.

To build a docker image, one just need to execute the build command:

```bash
docker build -t szz .
```
The dot indicates that the Dockerfile is located in the same directory which the command was executed in.

When the build is done, one can see the finished docker image with:
```bash
docker images
```

Now to actually do something, a docker container is needed. The docker container is the running instance, or if one prefer virtual machine, of the docker image. It will be a fully fledged system which runs an Alpine system. To start the container, just execute the command:
```bash
docker run -it --name szz_con szz ash
```

As a result, one is provided with a [ash shell](https://linux.die.net/man/1/ash) that is executed inside the docker container. With it, one can now run the steps required to generate the data. Here is a brief instruction how to generate data using the [jenkins project](https://github.com/jenkinsci/jenkins).

```bash
docker run -it --name ssz_con szz ash
cd /roo/fetch_jira_bugs
python3 fetch.py --issue-code JENKINS --jira-project issues.jenkins-ci.org
python3 git_log_to_array.py --repo-path ../jenkins --from-commit 02d6908ada70fcf8012833ddef628bc09c6f8389
python3 find_bug_fixes.py --gitlog ./gitlog.json --issue-list ./issues
cd /root/szz
java -jar ./build/libs/szz_find_bug_introducers-0.1.jar -i ../fetch_jira_bugs/issue_list.json -r ../jenkins
```

The results from the algorithm will now be located in */root/szz/results*. To get it, start another command prompt and execute the following command:

```bash
docker cp szz_con:/root/szz/results .
```