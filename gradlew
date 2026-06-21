#!/usr/bin/env sh

APP_HOME=$(cd "${0%/*}" && pwd -P) || exit
APP_NAME="Gradle"
JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ ! -r "$JAR" ]; then
  echo "Missing Gradle wrapper jar: $JAR" >&2
  exit 1
fi

exec java -classpath "$JAR" org.gradle.wrapper.GradleWrapperMain "$@"
