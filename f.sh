#!/bin/bash

# Syntax:  ./testfifteenpuzzle.sh <ip> <port> <size> <num-of-shuffles>
# Example: ./testfifteenpuzzle.sh 127.0.0.1 8000 4 20
HOST=$1
PORT=$2
size=$3
shuffles=$4

function test_single_requests {

	curl -X GET http://$HOST:$PORT/fifteenpuzzle\?size=$size\&shuffles=$shuffles
}

test_single_requests
