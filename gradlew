#!/bin/sh
# Gradle startup script for UN*X
APP_BASE_NAME=`basename "$0"`
DIRNAME=`dirname "$0"`
APP_HOME=`cd "$DIRNAME" && pwd`

if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

if [ -f "$CLASSPATH" ]; then
    exec "$JAVACMD" "-Dorg.gradle.appname=$APP_BASE_NAME" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
else
    exec gradle "$@"
fi
