#!/bin/bash
if [ "$#" -ne 1 ]; then
    echo "Pass the name of the runfile as the only argument to this script"
    echo "  Just the file name, NOT the full path. It must be in the current directory."
    exit 1
fi
RUNFILE=$1
set -v
set +e
rm evaluation_results.csv
touch evaluation_results.csv
chmod a+rw evaluation_results.csv
set -e
docker run --rm -v ${PWD}:/scratch gregorybrooks/evaluator bash -c "java -jar target/evaluator-1.0.0.jar AUTO.analytic_tasks.json /scratch/$RUNFILE req-qrels /scratch/evaluation_results.csv"
