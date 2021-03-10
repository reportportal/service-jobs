FROM openjdk:11-jre-slim

ENV JAVA_OPTS="-Xmx512m -Djava.security.egd=file:/dev/./urandom"
ENV JAVA_APP=/app.jar

RUN sh -c "echo $'#!/bin/sh \n\
exec java \$JAVA_OPTS -jar \$JAVA_APP' > /start.sh && chmod +x /start.sh"

VOLUME /tmp
ADD build/libs/service-jobs-*.jar $JAVA_APP

RUN sh -c 'touch $JAVA_APP'

EXPOSE 8080

ENTRYPOINT /start.sh