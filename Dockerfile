# syntax = docker/dockerfile:1.2
FROM ubuntu:jammy-20231004 AS graalvm

RUN apt-get update && apt-get upgrade -yqq && apt-get install -yqq --no-install-recommends curl tar openssl ca-certificates

# Install GraalVM
RUN curl -O https://download.oracle.com/graalvm/21/latest/graalvm-jdk-21  \
    && tar xzf graalvm-jdk-21_linux-x64_bin.tar.gz -C . \
    && rm -rf graalvm-jdk-21_linux-x64_bin.tar.gz

ENV JAVA_HOME="/graalvm-jdk-21+35.1/"
ENV PATH="${PATH}:${JAVA_HOME}/bin"