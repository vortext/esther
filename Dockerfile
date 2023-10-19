# syntax = docker/dockerfile:1.2
FROM ubuntu:jammy-20231004 AS build

RUN apt-get update && \
    apt-get upgrade -yqq && \
    apt-get install -yqq --no-install-recommends curl tar openssl ca-certificates git libsodium-dev

# Install a JVM
RUN curl -O https://download.oracle.com/graalvm/21/latest/graalvm-jdk-21_linux-x64_bin.tar.gz  \
    && tar xzf graalvm-jdk-21_linux-x64_bin.tar.gz -C . \
    && rm graalvm-jdk-21_linux-x64_bin.tar.gz

ENV JAVA_HOME=/graalvm-jdk-21.0.1+12.1/

# Install Clojure
RUN curl -L -O https://github.com/clojure/brew-install/releases/latest/download/linux-install.sh && \
    chmod +x linux-install.sh && \
    ./linux-install.sh && \
    rm ./linux-install.sh

WORKDIR /
COPY . /

RUN clojure -Sforce -T:build server
RUN clojure -T:build build-llama :blas 'clblast'



#
# # Install sqlite libsodium
# # Compile llama.cpp
#
# FROM ubuntu:jammy-20231004 AS exec
#
# COPY --from=build /target/esther-standalone.jar /esther/esther-standalone.jar
#
# EXPOSE $PORT
#
# ENTRYPOINT exec java $JAVA_OPTS -cp target/esther-standalone.jar clojure.main -m vortext.esther.core