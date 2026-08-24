# syntax=docker/dockerfile:1

FROM maven:3.9.3-eclipse-temurin-17-focal AS native-builder
# use kitware repo to get upper cmake version; fixes armv7l build
RUN curl -s https://apt.kitware.com/kitware-archive.sh | bash -s
RUN apt update && apt install -y git build-essential cmake

WORKDIR /app

COPY .git .git
COPY .gitmodules .gitmodules
COPY CMakeLists.txt .
COPY pom.xml .
COPY build_linux.sh .
COPY src src
COPY jfk.wav .

ARG TARGETARCH
ARG RUN_TESTS

RUN git submodule update --init
RUN TARGETARCH=${TARGETARCH} ./build_linux.sh

RUN mkdir -p /app/install
RUN cp src/main/resources/debian-*/*.so /app/install/

RUN if [ "$RUN_TESTS" = "true" ]; then mvn test; fi

FROM scratch AS export
COPY --from=native-builder /app/install/*.so /