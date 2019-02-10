FROM java:8-jdk-alpine

RUN apk add --no-cache --update python3 git

ADD . /root/

WORKDIR /root
RUN git clone https://github.com/jenkinsci/jenkins.git

RUN apk add --no-cache --update openjdk8 curl

RUN mkdir /usr/lib/gradle
WORKDIR /usr/lib/gradle
RUN set -x \
  && curl -L -O https://services.gradle.org/distributions/gradle-4.10.3-bin.zip \
  && unzip gradle-4.10.3-bin.zip

ENV PATH ${PATH}:/usr/lib/gradle/gradle-4.10.3/bin

WORKDIR /root/szz

RUN gradle build && gradle fatJar

WORKDIR /root
