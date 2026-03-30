#!/usr/bin/env sh

# This is a shell script to run Gradle 8.4

set -e

# Check if Gradle is installed
if ! [ -x "$(command -v gradle)" ]; then
  echo 'Error: Gradle is not installed.'
  exit 1
fi

# Run Gradle with the specified command
gradle "$@"