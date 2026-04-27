FROM --platform=$BUILDPLATFORM gradle:8.10.2-jdk21 AS build
ARG RELEASE_MODE
ARG APP_VERSION
WORKDIR /usr/app
RUN apt-get update \
    && apt-get install -y --no-install-recommends python3 python3-pip \
    && python3 -m pip install --upgrade "pip>=25.3" \
    && python3 -m pip install --upgrade --root-user-action=ignore "setuptools>=78.1.1" \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*
COPY . /usr/app
RUN if [ "${RELEASE_MODE}" = true ]; then \
    gradle build --exclude-task test \
        -PreleaseMode=true \
        -Dorg.gradle.project.version=${APP_VERSION}; \
    else gradle build --exclude-task test -Dorg.gradle.project.version=${APP_VERSION}; fi

FROM amazoncorretto:21.0.11
ARG APP_VERSION=dev
LABEL version=${APP_VERSION} description="EPAM Report portal. Jobs Service" maintainer="Andrei Varabyeu <andrei_varabyeu@epam.com>, Hleb Kanonik <hleb_kanonik@epam.com>"
ENV APP_DIR=/usr/app
ENV JAVA_OPTS="-Xmx1g -XX:+UseG1GC -XX:InitiatingHeapOccupancyPercent=70 -Djava.security.egd=file:/dev/./urandom"
WORKDIR $APP_DIR
COPY --from=build $APP_DIR/build/libs/service-jobs-*exec.jar .
VOLUME ["/tmp"]
EXPOSE 8686
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -jar ${APP_DIR}/service-jobs-*exec.jar"]
