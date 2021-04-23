set -v
docker rmi gregorybrooks/evaluator
docker rmi evaluator
docker build -t evaluator .
docker tag evaluator gregorybrooks/evaluator
