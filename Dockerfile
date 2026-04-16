FROM alpine:3.23.4
ENV GRADLE_OPTS="-Dkotlin.incremental=false -Dorg.gradle.daemon=false -Dorg.gradle.vfs.watch=false -Dorg.gradle.logging.stacktrace=full"

RUN apk add --no-cache \
      openjdk21 \
      ruby \
      tini \
 && gem install \
      rouge \
 && rm -rf /var/cache/* \
 && mkdir /var/cache/apk

WORKDIR /app

# Get the Gradle wrapper and cache the Gradle distribution first.
COPY gradlew settings.gradle ./
COPY gradle/wrapper ./gradle/wrapper
RUN ./gradlew --version

COPY gradle/libs.versions.toml ./gradle/libs.versions.toml
COPY build.gradle ./
COPY src/main ./src/main
RUN ./gradlew installDist

ENTRYPOINT ["/sbin/tini", "--", "/app/build/install/jakewharton.com/bin/jakewharton.com"]
CMD ["--help"]
