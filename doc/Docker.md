### How to run SZZ Unleashed using the provided Docker image

Docker is a great tool if one is using a system where either the Python or Java installation is not working properly. It requires Docker to be installed which is something that won't be covered here but can be found in Docker's own [installation instructions](https://docs.docker.com/install/). To get further information about what Docker is, read the overall Docker [documentation](https://docs.docker.com/).

In the root of this repository, there is a file called *Dockerfile*. Running this file builds a Docker image that contains all the components necessary for SZZ Unleashed to run. A Docker image is more or less a template for a future virtual machine that will run a certain system, in this case a small system called Alpine.

To build the Docker image, execute the build command in the root folder:

```bash
docker build -t szz .
```
The dot indicates that the Dockerfile is located in the same directory as where the build command is executed.

When the build is done, you can verify that the finished Docker image(s) have been created by running:
```bash
docker images
```

Now to actually do something, a Docker container is needed. The Docker container is the running instance, or if you prefer: virtual machine, of the Docker image. It is a fully fledged system that runs an Alpine system. To start the container, just execute the command:
```bash
docker run -it --name szz_con szz ash
```

As a result, you end up in an [ash shell](https://linux.die.net/man/1/ash) that is executed inside the Docker container. From the shell, you can now run the steps required to generate the data from SZZ Unleashed. Here are the commands to run to generate bug-introducing commits for the [Jenkins project](https://github.com/jenkinsci/jenkins).

```bash
docker run -it --name ssz_con szz ash
cd /root/fetch_jira_bugs
python3 fetch.py --issue-code JENKINS --jira-project issues.jenkins-ci.org
python3 git_log_to_array.py --repo-path ../jenkins --from-commit 02d6908ada70fcf8012833ddef628bc09c6f8389
python3 find_bug_fixes.py --gitlog ./gitlog.json --issue-list ./issues
cd /root/szz
java -jar ./build/libs/szz_find_bug_introducers-0.1.jar -i ../fetch_jira_bugs/issue_list.json -r ../jenkins
```

The results from the algorithm will now be located in */root/szz/results*. To copy them to your current directory, start another command prompt and execute the following command:

```bash
docker cp szz_con:/root/szz/results .
```
