FROM openjdk:11-jre-slim
LABEL version=5.4.0 description="EPAM Report portal. Main API Service" maintainer="Andrei Varabyeu <andrei_varabyeu@epam.com>"
ARG GH_TOKEN
RUN apt-get update -qq && apt-get install -qq -y wget && \
	echo 'exec java ${JAVA_OPTS} -jar service-jobs-5.4.0-exec.jar' > /start.sh && chmod +x /start.sh && \
	wget --header="Authorization: Bearer ${GH_TOKEN}"  -q https://maven.pkg.github.com/reportportal/service-jobs/com/epam/reportportal/service-jobs/5.4.0/service-jobs-5.4.0-exec.jar
ENV JAVA_OPTS="-Xmx512m -XX:+UseG1GC -XX:InitiatingHeapOccupancyPercent=70 -Djava.security.egd=file:/dev/./urandom"
VOLUME ["/tmp"]
EXPOSE 8080
ENTRYPOINT ./start.sh
