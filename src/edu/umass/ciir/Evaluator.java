package edu.umass.ciir;

import org.json.simple.parser.ParseException;

import java.io.*;
import java.util.*;

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

    /**
     * Evaluates the query formulations and outputs a CSV file of evaluation results.
     * Evaluation Type 1 (E1) uses the docs judged to be REQUEST RELEVANT only as the known relevant
     * docs for a request. Evaluation Type 2 (E2) uses the docs judged to be TASK RELEVANT or
     * REQUEST RELEVANT as the known relevant docs for a request.
     *
     * For averaging the evaluation results, we use the MACRO approach:
     * MACRO averages the results at the task level, accumulates those results and
     * divides the totals by the number of tasks.
     * @throws IOException
     * @throws InterruptedException
     * @throws ParseException
     */
    private void process(String[] args) throws IOException, InterruptedException, ParseException {
        Pathnames.getPathnames();
        String taskFileName = args[0];

        AnalyticTasks tasks = new AnalyticTasks(taskFileName);
        tasks.fixTaskDocs();  //Make sure task-docs contains all req-docs for that task

        /* This is the list of query formulations to process.
        These should be the query formulations that did NOT reject the task-docs
        and request-docs. These are the "...-TEST" versions for us, and for the other
        player's files which apparently did not reject the hint docs anyway I copied them
        to the same directory and used our naming convention, so we can evaluate them, too.
         */
        List<String> queryFormulations = new ArrayList<String>(Arrays.asList(
                "CLEAR-BASE-TEST",
                "CLEAR-2-TEST",
                "CLEAR-1-TEST",
                "CLEAR-3-TEST",
                "CLEAR-4-TEST",
                "CLEAR-5-TEST",
                "CLEAR-6-TEST",
                "CLEAR-7-TEST",
                "CLEAR-8-TEST",
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
                10, 20, 50, 100, 250, 500, 1000
        ));

        /* Create and open the output CSV file */
        FileWriter csvWriter = new FileWriter(evaluationFileLocation + "/comparison.csv");
        /* Write the header line */
        csvWriter.append("Solution");
        csvWriter.append(",");
        csvWriter.append("Judgment Set Used");
        csvWriter.append(",");
        csvWriter.append("Result Set Size");
        csvWriter.append(",");
        csvWriter.append("Recall (Pct)");
        csvWriter.append(",");
        csvWriter.append("Precision (Pct)");
        csvWriter.append(",");
        csvWriter.append("R Precision (Pct)");
        csvWriter.append(",");
        csvWriter.append("Unjudged (Pct)");
        csvWriter.append("\n");

        for (Integer rsize : resultSetSizes) {
            for (String queryFormulationName : queryFormulations) {
                QuerySet queryFormulation = new QuerySet(taskFileName + "." + queryFormulationName);

                System.out.println("Evaluating solution " + queryFormulation.getName() + " for top "
                    + rsize + " results");

                // Assume every formulation includes all requests defined in the analytic task file.
                List<String> requestIDs = tasks.getRequestIDs();
                ListIterator<String> requestIDIterator = requestIDs.listIterator();

                /* macro averaging approach accumulators */
                double totalTaskE1Recall = 0.0;
                double totalTaskE1Precision = 0.0;
                double totalTaskE1RPrecision = 0.0;
                double totalTaskE1Unjudged = 0.0;
                double totalTaskE2Recall = 0.0;
                double totalTaskE2Precision = 0.0;
                double totalTaskE2RPrecision = 0.0;
                double totalTaskE2Unjudged = 0.0;

                int totalTasks = 0;  // this will be calculated as we go through the requests
                String prevTaskID = "EMPTY";  // used to detect Task changes
                String taskID = "";  // used to detect Task changes
                int totalRequestsInTask = 0;  // this will be calculated as we go through the requests

                /* macro averaging approach task-level accumulators */
                double taskE1Recall = 0.0;
                double taskE1Precision = 0.0;
                double taskE1RPrecision = 0.0;
                double taskE1Unjudged = 0.0;
                double taskE2Recall = 0.0;
                double taskE2Precision = 0.0;
                double taskE2RPrecision = 0.0;
                double taskE2Unjudged = 0.0;

                /* Assumption: all requests for a task are contiguous as we iterate them */
                while (requestIDIterator.hasNext()) {
                    String requestID = requestIDIterator.next();
                    List<String> runDocids = queryFormulation.getDocids(requestID, rsize);
                    taskID = requestID.substring(0, 5);

                    if (!taskID.equals("DR-T1"))  //TEMP - only Task 1 has been judged
                        continue;  // TEMP

                    if (!taskID.equals(prevTaskID) && !prevTaskID.equals("EMPTY")) {
                        ++totalTasks;
                        totalTaskE1Recall += (taskE1Recall / totalRequestsInTask);
                        totalTaskE1Precision += (taskE1Precision / totalRequestsInTask);
                        totalTaskE1RPrecision += (taskE1RPrecision / totalRequestsInTask);
                        totalTaskE1Unjudged += (taskE1Unjudged / totalRequestsInTask);
                        totalTaskE2Recall += (taskE2Recall / totalRequestsInTask);
                        totalTaskE2Precision += (taskE2Precision / totalRequestsInTask);
                        totalTaskE2RPrecision += (taskE2RPrecision / totalRequestsInTask);
                        totalTaskE2Unjudged += (taskE2Unjudged / totalRequestsInTask);
                        taskE1Recall = 0.0;
                        taskE1Precision = 0.0;
                        taskE1RPrecision = 0.0;
                        taskE1Unjudged = 0.0;
                        taskE2Recall = 0.0;
                        taskE2Precision = 0.0;
                        taskE2RPrecision = 0.0;
                        taskE2Unjudged = 0.0;
                        totalRequestsInTask = 0;
                    }
                    prevTaskID = taskID;
                    ++totalRequestsInTask;

                    List<String> reqDocList = tasks.getRequestRelevantDocids(requestID);
                    List<String> taskDocList = tasks.getTaskAndRequestRelevantDocids(requestID);
                    if (reqDocList.size() == 0 || taskDocList.size() == 0) {
                        continue;
                    }
                    int taskDocsFound = 0;
                    int requestDocsFound = 0;
                    int unjudgedDocsFound = 0;
                    for (String s : taskDocList) {
                        if (runDocids.contains(s)) {
                            ++taskDocsFound;
                        }
                    }
                    for (String s : reqDocList) {
                        if (runDocids.contains(s)) {
                            ++requestDocsFound;
                        }
                    }
                    for (String s : runDocids) {
                        if (tasks.getRelevanceJudgment(requestID, s) == null) {
                            ++unjudgedDocsFound;
                        }
                    }


                    /* E1 is request level, E2 is task level */
                    double e1Recall = ((double) requestDocsFound / reqDocList.size()) * 100;
                    double e2Recall = ((double) taskDocsFound / taskDocList.size()) * 100;
                    double e1Precision = ((double) requestDocsFound / runDocids.size()) * 100;
                    double e2Precision = ((double) taskDocsFound / runDocids.size()) * 100;
                    double e1Unjudged = ((double) unjudgedDocsFound / runDocids.size()) * 100;
                    double e2Unjudged = ((double) unjudgedDocsFound / runDocids.size()) * 100;

                    String[] runDocidsAsArray = new String[runDocids.size()];
                    runDocidsAsArray = runDocids.toArray(runDocidsAsArray);

                    /* How many request docs are in the top N hits? (E1) */
                    int docMatches = 0;
                    for (int x = 0; x < reqDocList.size() && x < runDocids.size(); ++x) {
                        if (reqDocList.contains(runDocidsAsArray[x])) {
                            docMatches += 1;
                        }
                    }
                    double e1RPrecision = ((double) docMatches / reqDocList.size()) * 100;

                    /* How many task docs are in the top N hits? (E2) */
                    docMatches = 0;
                    for (int x = 0; x < taskDocList.size() && x < runDocids.size(); ++x) {
                        if (taskDocList.contains(runDocidsAsArray[x])) {
                            docMatches += 1;
                        }
                    }
                    double e2RPrecision = ((double) docMatches / taskDocList.size()) * 100;

                    /* Accumulators for macro averaging approach */
                    taskE1Precision += e1Precision;
                    taskE2Precision += e2Precision;
                    taskE1Recall += e1Recall;
                    taskE2Recall += e2Recall;
                    taskE1RPrecision += e1RPrecision;
                    taskE2RPrecision += e2RPrecision;
                    taskE1Unjudged += e1Unjudged;
                    taskE2Unjudged += e2Unjudged;

                }
                /* Flush out that last Task */
                if (!prevTaskID.equals("EMPTY")) {
                    ++totalTasks;
                    totalTaskE1Recall += (taskE1Recall / totalRequestsInTask);
                    totalTaskE1Precision += (taskE1Precision / totalRequestsInTask);
                    totalTaskE1RPrecision += (taskE1RPrecision / totalRequestsInTask);
                    totalTaskE1Unjudged += (taskE1Unjudged / totalRequestsInTask);
                    totalTaskE2Recall += (taskE2Recall / totalRequestsInTask);
                    totalTaskE2Precision += (taskE2Precision / totalRequestsInTask);
                    totalTaskE2RPrecision += (taskE2RPrecision / totalRequestsInTask);
                    totalTaskE2Unjudged += (taskE2Unjudged / totalRequestsInTask);
                }

                double macroAvgE1Recall = totalTaskE1Recall / totalTasks;
                double macroAvgE1Precision = totalTaskE1Precision / totalTasks;
                double macroAvgE1RPrecision = totalTaskE1RPrecision / totalTasks;
                double macroAvgE1Unjudged = totalTaskE1Unjudged / totalTasks;
                double macroAvgE2Recall = totalTaskE2Recall / totalTasks;
                double macroAvgE2Precision = totalTaskE2Precision / totalTasks;
                double macroAvgE2RPrecision = totalTaskE2RPrecision / totalTasks;
                double macroAvgE2Unjudged = totalTaskE2Unjudged / totalTasks;

                csvWriter.append(queryFormulationName);
                csvWriter.append(",");
                csvWriter.append("REQUEST RELEVANT");
                csvWriter.append(",");
                csvWriter.append(Integer.toString(rsize));
                csvWriter.append(",");
                csvWriter.append(String.format("%.2f", macroAvgE1Recall));
                csvWriter.append(",");
                csvWriter.append(String.format("%.2f", macroAvgE1Precision));
                csvWriter.append(",");
                csvWriter.append(String.format("%.2f", macroAvgE1RPrecision));
                csvWriter.append(",");
                csvWriter.append(String.format("%.2f", macroAvgE1Unjudged));
                csvWriter.append("\n");

                csvWriter.append(queryFormulationName);
                csvWriter.append(",");
                csvWriter.append("TASK OR REQUEST RELEVANT");
                csvWriter.append(",");
                csvWriter.append(Integer.toString(rsize));
                csvWriter.append(",");
                csvWriter.append(String.format("%.2f", macroAvgE2Recall));
                csvWriter.append(",");
                csvWriter.append(String.format("%.2f", macroAvgE2Precision));
                csvWriter.append(",");
                csvWriter.append(String.format("%.2f", macroAvgE2RPrecision));
                csvWriter.append(",");
                csvWriter.append(String.format("%.2f", macroAvgE2Unjudged));
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
    public static void main(String[] args) throws IOException, InterruptedException, ParseException {
        Evaluator evaluator = new Evaluator();
        evaluator.process(args);
    }
}