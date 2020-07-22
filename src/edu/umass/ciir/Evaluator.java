package edu.umass.ciir;

import java.io.*;
import java.util.*;
import org.json.simple.parser.ParseException;

import static edu.umass.ciir.Pathnames.evaluationFileLocation;

/**
 * Evaluates the query formulations and outputs a CSV file of evaluation results.
 * Evaluation Type 1 (E1) uses the docs judged to be REQUEST RELEVANT only as the known relevant
 * docs for a request. Evaluation Type 2 (E2) uses the docs judged to be TASK RELEVANT or
 * REQUEST RELEVANT as the known relevant docs for a request.
 *
 * For averaging the evaluation results, we use the MACRO approach:
 * MACRO averages the results at the task level, accumulates those results and
 * divides the totals by the number of tasks.
 */
public class Evaluator {
    AnalyticTasks tasks = null;

    /**
     * Calculates the normalized discounted cumulative gain
     * for this request and this ranked set of docs.
     * @param requestID The request, with its relevance judgments available.
     * @param runDocids The ranked hits.
     * @return The calculated nDCG.
     */
    private double calculatenDCG( String requestID, List<String> runDocids) {
        List<RelevanceJudgment> judgments = tasks.getRelevanceJudgments(requestID);
        if (runDocids.size() < judgments.size()) {
             return 0.0;  // can't calc it
        }
        int cutoff = judgments.size();
        /* Calculate the ideal discounted cumulative gain for this query */
        judgments.sort(Comparator.comparingInt(RelevanceJudgment::getRelevanceJudgmentValue)
                .reversed());
        double iDCG = 0.0;
        int index = 1;
        for (RelevanceJudgment j : judgments) {
            if (index == 1) {
                iDCG += j.getRelevanceJudgmentValue();
            }
            else {
                iDCG += (j.getRelevanceJudgmentValue() / ((Math.log(index + 1) / Math.log(2))));

            }
            ++index;
        }
        /* Calculate discounted cumulative gain of the ranked hits */
        double DCG = 0.0;
        index = 1;
        for (String docid : runDocids) {
            if (index == 1) {
                DCG += tasks.getRelevanceJudgmentValue(requestID, docid);
            }
            else {
                DCG += (tasks.getRelevanceJudgmentValue(requestID, docid)
                        / ((Math.log(index + 1) / Math.log(2))));

            }
            ++index;
            if (index > cutoff) {
                break;
            }
        }
        /* Calculate the normalized discounted cumulative gain */
        double nCDG = DCG / iDCG;
        return nCDG;
    }

    /**
     * Evaluates the query formulations and outputs a CSV file of evaluation results.
     * This version only calculates nDCG.
     *
     * For averaging the evaluation results, we use the MICRO approach.
     * @throws IOException
     * @throws InterruptedException
     * @throws ParseException
     */
    private void process(String[] args) throws IOException, ParseException {
        Pathnames.getPathnames();
        String taskFileName = args[0];

        tasks = new AnalyticTasks(taskFileName);
        tasks.fixTaskDocs();  //Make sure task-docs contains all req-docs for that task

        /* This is the list of query formulations to process.
        These should be the query formulations that did NOT reject the task-docs
        and request-docs. These are the "...-TEST" versions for us, and for the other
        player's files which apparently did not reject the hint docs anyway I copied them
        to the same directory and used our naming convention, so we can evaluate them, too.
         */
        List<String> queryFormulations = new ArrayList<String>(Arrays.asList(
//                "CLEAR-BASE-TEST",
                "CLEAR-2",
                "CLEAR-1",
                "CLEAR-3",
                "CLEAR-4",
                "CLEAR-5",
                "CLEAR-6",
                "CLEAR-7",
                "CLEAR-8",
                "BBN-1",
                "JHU-1",
                "BROWN-1"
                ));
        /*
        This is the list of result-set sizes we want to evaluate, for each solution.
        We calculate recall, precision and R precision for the top N hits, where N is each of the sizes
        in this list.
         */
        List<Integer> resultSetSizes = new ArrayList<Integer>(Arrays.asList(
                1000
        ));

        /* Create and open the output CSV file */
        FileWriter csvWriter = new FileWriter(evaluationFileLocation + "/comparison_all_nosampledocs_ndcg.csv");
        /* Write the header line */
        csvWriter.append("Solution");
        csvWriter.append(",");
        csvWriter.append("Normalized DCG - MICRO");
        csvWriter.append(",");
        csvWriter.append("Normalized DCG - MACRO");
        csvWriter.append("\n");

        for (Integer rsize : resultSetSizes) {
            for (String queryFormulationName : queryFormulations) {
                QuerySet queryFormulation = new QuerySet(taskFileName + "." + queryFormulationName);

                System.out.println("Evaluating solution " + queryFormulation.getName() + " for top "
                    + rsize + " results");

                List<String> requestIDs = tasks.getRequestIDs();
                ListIterator<String> requestIDIterator = requestIDs.listIterator();

                /* macro averaging approach accumulators */
                double totalTasknCDG = 0.0;

                int totalRequests = 0;
                int totalTasks = 0;  // this will be calculated as we go through the requests
                String prevTaskID = "EMPTY";  // used to detect Task changes
                String taskID = "";  // used to detect Task changes
                int totalRequestsInTask = 0;  // this will be calculated as we go through the requests

                /* macro averaging approach task-level accumulators */
                double tasknCDG = 0.0;

                double totalnDCG = 0.0;

                /* Assumption: all requests for a task are contiguous as we iterate them */
                while (requestIDIterator.hasNext()) {
                    String requestID = requestIDIterator.next();
                    List<String> runDocids = queryFormulation.getDocids(requestID, rsize);
                    taskID = requestID.substring(0, 5);

                    if (!taskID.equals(prevTaskID) && !prevTaskID.equals("EMPTY")) {
                        ++totalTasks;
                        totalTasknCDG += (tasknCDG / totalRequestsInTask);
                        tasknCDG = 0.0;
                        totalRequestsInTask = 0;
                    }
                    prevTaskID = taskID;

                    List<String> reqDocList = tasks.getRequestRelevantDocids(requestID);
                    List<String> taskDocList = tasks.getTaskAndRequestRelevantDocids(requestID);

                    /* If we have no relevance judgments for this query, skip evaluating it */
                    if (reqDocList.size() == 0 || taskDocList.size() == 0) {
                        continue;
                    }
                    ++totalRequests;
                    ++totalRequestsInTask;

                    double nDCG = calculatenDCG(requestID, runDocids);
                    totalnDCG += nDCG;
                    /* Accumulators for macro averaging approach */
                    tasknCDG += nDCG;

                }
                /* Flush out that last Task */
                if (!prevTaskID.equals("EMPTY")) {
                    ++totalTasks;
                    totalTasknCDG += (tasknCDG / totalRequestsInTask);
                }

                double macroAvgnDCG = totalTasknCDG / totalTasks;
                double microAvgnDCG = totalnDCG / totalRequests;

                csvWriter.append(queryFormulationName);
                csvWriter.append(",");
                csvWriter.append(String.format("%.2f", microAvgnDCG));
                csvWriter.append(",");
                csvWriter.append(String.format("%.2f", macroAvgnDCG));
                csvWriter.append("\n");

                csvWriter.flush();

            }
        }
        csvWriter.close();
    }

    /**
     * Public entry point for the program.
     * @param args No arguments are expected.
     */
    public static void main(String[] args) throws IOException, ParseException {
        Evaluator evaluator = new Evaluator();
        evaluator.process(args);
    }
}