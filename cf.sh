#!/bin/bash

# Syntax:  ./testcapturetheflag.sh <ip> <port> <grid-size> <num-blue-agents> <num-red-agents> <flag-placement-type>
# Example: ./testcapturetheflag.sh 127.0.0.1 8000 20 5 5 A
HOST=$1
PORT=$2
gridSize=$3
numRedAgents=$4
numBlueAgents=$5
flagPlacementType=$6

function test_single_requests {

	curl -X GET http://$HOST:$PORT/capturetheflag\?gridSize=$gridSize\&numBlueAgents=$numBlueAgents\&numRedAgents=$numRedAgents\&flagPlacementType=$flagPlacementType
}

test_single_requests
