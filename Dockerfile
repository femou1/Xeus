# Build container

FROM gradle:4.10.2-jdk11-slim AS build

COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle build --no-daemon

# Run container

FROM openjdk:16-ea-23-jdk-oraclelinux8 AS runtime

WORKDIR /opt/avaire/

RUN adduser --disabled-password --gecos '' xeus; \
    chown xeus:xeus -R /opt/xeus; \
    chmod u+w /opt/xeus; \
    chmod 0755 -R /opt/xeus

USER xeus

COPY --from=build /home/gradle/src/Xeus.jar /bin/

CMD ["java","-jar","/bin/Xeus.jar","-env","--use-plugin-index"]
