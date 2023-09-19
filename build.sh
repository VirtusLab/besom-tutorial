#!/usr/bin/env bash

set -euo pipefail

# check uname for Darwin or Cygwin, if so - warn and exit
if [[ $(uname) == "Darwin" || $(uname) == "CYGWIN"* ]]; then
  echo "This project be only built on linux due to GraalVM native-image limitations (https://github.com/oracle/graal/issues/407)."
  echo "Use pre-built lambda zip packages in ./pre-built directory if you're on Mac or Windows."
  exit 1
fi

scala-cli compile lambda

scala-cli package --native-image lambda --main-class besom.examples.lambda.renderFeedMain -o renderFeed -f

scala-cli package --native-image lambda --main-class besom.examples.lambda.postCatEntryMain -o postCatEntry -f

rm -f post-cat-entry.zip
rm -f render-feed.zip

mv renderFeed bootstrap
zip render-feed.zip bootstrap

rm -f bootstrap

mv postCatEntry bootstrap
zip post-cat-entry.zip bootstrap

rm -f bootstrap