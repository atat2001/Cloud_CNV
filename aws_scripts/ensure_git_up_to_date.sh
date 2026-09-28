#!/bin/bash
source config.sh


git clone "$AUTH_REPO_URL"
cd "$REPO_DIR" || exit 1
git pull "$AUTH_REPO_URL"