#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_DIR"
"$PROJECT_DIR/scripts/mvn-java17.sh" -q \
  org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath \
  -Dmdep.includeScope=compile \
  -Dmdep.outputFile=target/bcrypt-classpath

CLASSPATH="$(cat target/bcrypt-classpath)"
mkdir -p target/bcrypt-tool
"/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home/bin/javac" \
  -cp "$CLASSPATH" -d target/bcrypt-tool scripts/GenerateBcryptHash.java
"/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home/bin/java" \
  -cp "target/bcrypt-tool:$CLASSPATH" GenerateBcryptHash
