#!/bin/sh
set -eu
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$CLASSPATH" ]; then
    echo "Missing gradle/wrapper/gradle-wrapper.jar. Re-download or regenerate the Gradle wrapper." >&2
    exit 1
fi
if [ -n "${JAVA_HOME:-}" ]; then
    JAVA="$JAVA_HOME/bin/java"
else
    JAVA="java"
fi
exec "$JAVA" ${JAVA_OPTS:-} ${GRADLE_OPTS:-} -classpath "$CLASSPATH" \
    -Dorg.gradle.appname=gradlew org.gradle.wrapper.GradleWrapperMain "$@"
