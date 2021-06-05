#!/bin/bash

if [ -z "$CONCOURSE_TEAM" ]; then
  echo "CONCOURSE_TEAM is undefined. Using \`josdk-spring-boot-starter\`"
  export CONCOURSE_TEAM="josdk-spring-boot-starter"
fi


export DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

set -x

fly \
  -t $CONCOURSE_TEAM \
  set-pipeline \
  -p josdk-spring-boot-starter-main \
  -c $DIR/josdk-spring-boot-starter.yml