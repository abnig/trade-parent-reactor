#!/usr/bin/env zsh
set -euo pipefail

script_dir=${0:A:h}
cd "$script_dir/.."

# Surefire HTML reporting is not thread-safe, so generate it only after all
# parallel test execution and coverage generation have completed.
mvn -T "${MAVEN_THREADS:-1C}" clean verify "$@"
mvn -Phtml-test-reports surefire-report:report-only
