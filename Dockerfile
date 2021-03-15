FROM openjdk:11-jre-slim
LABEL version=0.0.1 description="EPAM Report portal. Main API Service" maintainer="Andrei Varabyeu <andrei_varabyeu@epam.com>"
RUN echo 'exec java ${JAVA_OPTS} -jar service-jobs-0.0.1-exec.jar' > /start.sh && chmod +x /start.sh && \
	wget -q https://dl.bintray.com/epam/reportportal/com/epam/reportportal/service-jobs/0.0.1/service-jobs-0.0.1-exec.jar
ENV JAVA_OPTS=
VOLUME ["/tmp"]
EXPOSE 8080
ENTRYPOINT ./start.sh
