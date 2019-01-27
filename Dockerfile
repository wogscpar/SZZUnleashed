FROM java:8-jdk-alpine

RUN apk add --no-cache --update python3 git

ADD . /root/

WORKDIR /root
RUN git clone https://github.com/jenkinsci/jenkins.git

WORKDIR /root/fetch_jira_bugs
RUN python3 fetch.py
RUN python3 git_log_to_array.py --repo-path ../jenkins
RUN python3 find_bug_fixes.py --gitlog ./gitlog.json --issue-list ./issues

WORKDIR /root/szz

RUN apk add --no-cache --update openjdk8 curl

RUN mkdir /usr/lib/gradle
WORKDIR /usr/lib/gradle
RUN set -x \
  && curl -L -O https://services.gradle.org/distributions/gradle-4.10.3-bin.zip \
  && unzip gradle-4.10.3-bin.zip

ENV PATH ${PATH}:/usr/lib/gradle/gradle-4.10.3/bin

WORKDIR /root/szz

RUN gradle build && gradle fatJar
RUN java -jar ./build/libs/szz_find_bug_introducers-0.1.jar -i ../fetch_jira_bugs/issue_list.json -r ../jenkins

WORKDIR /root