#!/bin/sh
# ----------------------------------------------------------------------------
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.
# Maven Wrapper Shell Script, please do not modify.
# ----------------------------------------------------------------------------

if [ -z "$JAVA_HOME" ]; then
  javaExecutable="`which java`"
  if [ -n "$javaExecutable" ] && ! [ "`ls -1d /proc/0`" = "/proc/0" ]; then
    readLink=`which readlink`
    if [ ! `uname -s` = "Darwin" ]; then
      javaExecutable="`readlink -f "$javaExecutable"`"
    fi
    javaHome="`dirname "$javaExecutable"`"
    javaHome=`expr "$javaHome" : '\(.*\)/bin'`
    JAVA_HOME="$javaHome"
    export JAVA_HOME
  fi
fi

MAVEN_OPTS="${MAVEN_OPTS:-}"
exec mvn "$@"
