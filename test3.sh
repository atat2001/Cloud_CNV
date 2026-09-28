#!/bin/bash
ip="51.44.15.40"
./cf.sh "$ip" "8000" "20" "5" "5" "A"
./f.sh "$ip" "8000" "4" "20"
./gol.sh "$ip" "8000" "r" "glider-10-10.json 10"
