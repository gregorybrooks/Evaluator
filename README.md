# Evaluator
Evaluate a runfile using the BETTER nDCG@R scoring method.

This program is also in Docker Hub as gregorybrooks/evaluator.

Put the runfile to be evaluated in the current directory and execute run_docker.sh, 
passing the unqualified name of the runfile as the only parm to the script:

./run_docker.sh myrunfile.out

It will leave a .csv file called 'evaluation_results.csv' in the current directory.
The file has the nDCG@R for each request, and the average across all requests in 
a TOTAL line at the end.
