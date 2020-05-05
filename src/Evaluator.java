import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 */
public class Evaluator {
    /**
     * The list of solutions (these are files that were output by Galago's batch-search.)
     */
    private static final String resultsFilesDirectory = "/home/glbrooks/BETTER/";
    private static final String queriesFilesDirectory = "/home/glbrooks/BETTER/tools/ConvertDryRunTasks/test_data/";

    /**
     * The shorthand names for the query formulation solutions generated by ConvertDryRunTasks.
     */
    private final List<String> solutionNames = new ArrayList<String>(Arrays.asList(
            "CLEAR-BASE-TEST",
            "CLEAR-1-TEST",
            "CLEAR-2-TEST",
            "CLEAR-3-TEST",
            "CLEAR-4-TEST",
            "CLEAR-5-TEST",
            "CLEAR-6-TEST",
            "BBN-1",
            "JHU-1",
            "BROWN-1"
            ));

    /**
     * The file that contains the task and request definitions.
     */
    private static final String runType = "auto";
    private static final String tasksAndRequestsFile = queriesFilesDirectory + "dry-run-topics."
        + runType + ".json";

    /**
     * The tasks in the tasksAndRequestsFile converted into a Map of Task objects.
     */
    private final Map<String,Task> tasks = new HashMap<>();

    /**
     * The solution results after being converted to Solution objects.
     */
    private final Map<String,Solution> solutionResults = new HashMap<>();

    private Integer resultSetSize = -1;  // number of hits to look at

    /**
     * Within the solution are multiple Tasks, and each Task has multiple Requests.
     * This class represents the results of running the query for a Request.
     */
    private class RequestRun {
        private String queryID;
        private List<String> docids;
        RequestRun(String queryID, List<String> docids) {
            this.queryID = queryID;
            this.docids = docids;
        }
        String getQueryID() { return queryID; }
        List<String> getDocids() { return docids; }
        public String toString() {
            return "Request " + queryID + ": " + docids;
        }
    }

    /**
     * Represents the results of a particular query formulation's Galago execution.
     */
    private class Solution {
        private List<RequestRun> requestRuns;
        private String name;
        Solution (String name, List<RequestRun> requestRuns) {
            this.requestRuns = requestRuns;
            this.name = name;
        }
        List<RequestRun> getRequestRuns() {
            return requestRuns;
        }
        String getName() {
            return name;
        }
        public String toString() {
            return "Solution " + name + ": " + requestRuns;
        }
    }

    /**
     * Reads the Dry Run JSON file containing the analytic tasks and requests to be processed
     * and constructs a Map of Tasks and Requests that represent them.
     * @throws IOException
     * @throws ParseException
     */
    private void loadTasks() throws IOException, ParseException {
        Reader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(tasksAndRequestsFile)));
        JSONParser parser = new JSONParser();
        JSONArray tasksJSON = (JSONArray) parser.parse(reader);
        for (Object oTask : tasksJSON) {
            Task t = new Task((JSONObject) oTask);  // this gets Requests, too
            tasks.put(t.taskNum, t);
        }
    }

    private Task findTask(String taskNum) {
        return tasks.get(taskNum);
    }

    private Request findRequest(Task t, String requestNum) {
        return t.requests.get(requestNum);
    }

    /**
     * Filters certain characters that cause problems for the Galago query.
     * @param q
     * @return
     */
    public static String filterCertainCharacters(String q) {
        if (q == null || q.length() == 0) {
            return q;
        }
        else {
            q = q.replaceAll("\\.", "");  // with the periods the queries hang
            q = q.replaceAll("\\(", "");  // parentheses are included in the token
            q = q.replaceAll("\\)", "");  // which causes that term to not be matched
            //          q = q.replaceAll("\u2019", "'");  // this single-quote is in the original doc
            return q;
        }
    }

    /**
     * Reads in the query results files for all the solutions.
     * The solutions are converted into Solution objects and stored into a map
     * at solutionResults. Key is the solution name, e.g. CLEAR-1.
     */
    private void loadSolutionResults() {
        for (String s : solutionNames) {
            Solution solution = readQueryResultsFile(s);
            solutionResults.put(s, solution);
        }
    }

    /**
     * Reads in the file that was output from Galago's batch-search function,
     * which is the top x hits for each of the Requests in the input file.
     * @param solution The name of the solution, e.g. CLEAR-1.
     * @return A Solution object that captures the query results.
     */
    private Solution readQueryResultsFile(String solution)  {
        String fileName = resultsFilesDirectory + "dry-run-topics." + runType
                 + "." + solution + ".out";
        List<RequestRun> requestRuns = new ArrayList<>();
        List<String> docids = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
            String line;
            String prevQueryID = "NONE";
            int docidsAdded = 0;
            while ((line = br.readLine()) != null) {
                String queryID = line.split("[ \t]+")[0];
                if (!prevQueryID.equals(queryID)) {
                    if (!prevQueryID.equals("NONE")) {
                        // Clone the list
                        List<String> cloned_list
                                = new ArrayList<String>(docids);
                        RequestRun r = new RequestRun(prevQueryID, cloned_list);
                        requestRuns.add(r);
                        docids.clear();
                        docidsAdded = 0;
                    }
                }
                prevQueryID = queryID;
                if (docidsAdded < resultSetSize) {
                    docids.add(line.split("[ \t]+")[2]); // doc ID is 3rd field
                    ++docidsAdded;
                }
            }
            if (!prevQueryID.equalsIgnoreCase("")) {
                // Clone the list
                List<String> cloned_list
                        = new ArrayList<String>(docids);
                RequestRun r = new RequestRun(prevQueryID, cloned_list);
                requestRuns.add(r);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new Solution(solution, requestRuns);
    }

    private void fixTaskDocs() {
        tasks.forEach((k,v)->{
            String taskName = k;
            Task t = v;
//            System.out.println("Task " + taskName);
            t.requests.forEach((rk,rv)->{
                String requestName = rk;
                Request r = rv;
//                System.out.println("Request " + requestName);
                r.reqDocList.forEach((d)->{
                    if (!t.taskDocList.contains(d)) {
/*
                        System.out.println("Found a request doc not in task docs");
                        System.out.println("Task docs:");
                        System.out.println(t.taskDocList);
                        System.out.println("Request docs:");
                        System.out.println(r.reqDocList);
*/
                        t.taskDocList.add(d);
                    }
                });
            });
        });
    }

    /**
     */
    private void process() throws IOException, InterruptedException, ParseException {
        loadTasks();
        fixTaskDocs();  //Make sure task-docs contains all req-docs

        List<String> solutions = new ArrayList<String>(Arrays.asList(
                "CLEAR-BASE-TEST",
                "CLEAR-2-TEST",
                "CLEAR-1-TEST",
                "CLEAR-3-TEST",
                "CLEAR-4-TEST",
                "CLEAR-5-TEST",
                "CLEAR-6-TEST",
                "BBN-1",
                "JHU-1",
                "BROWN-1"
                ));
        List<Integer> resultSetSizes = new ArrayList<Integer>(Arrays.asList(
                10, 20, 50, 100, 250, 500, 1000
        ));

        FileWriter csvWriter = new FileWriter("/home/glbrooks/comparison.csv");
        csvWriter.append("Solution");
        csvWriter.append(",");
        csvWriter.append("Relevant Docs Used");
        csvWriter.append(",");
        csvWriter.append("Result Set Size");
        csvWriter.append(",");
        /*
        csvWriter.append("Averaging Type");
        csvWriter.append(",");
        */
        csvWriter.append("Recall (Pct)");
        csvWriter.append(",");
        csvWriter.append("Precision (Pct)");
        csvWriter.append(",");
        csvWriter.append("R Precision (Pct)");
        csvWriter.append("\n");
        for (Integer rsize : resultSetSizes) {
            resultSetSize = rsize;
            loadSolutionResults();
            for (String solutionName : solutions) {
                Solution s1 = solutionResults.get(solutionName);
                System.out.println("Evaluating solution " + s1.getName());
                List<RequestRun> s1RequestRuns = s1.getRequestRuns();
                ListIterator<RequestRun> s1RequestsIterator = s1RequestRuns.listIterator();
                double totalE1Recall = 0.0;
                double totalE2Recall = 0.0;
                double totalE1Precision = 0.0;
                double totalE2Precision = 0.0;
                double totalE1RPrecision = 0.0;
                double totalE2RPrecision = 0.0;

                double totalTaskE1Recall = 0.0;
                double totalTaskE1Precision = 0.0;
                double totalTaskE1RPrecision = 0.0;
                double totalTaskE2Recall = 0.0;
                double totalTaskE2Precision = 0.0;
                double totalTaskE2RPrecision = 0.0;

                int numRequests = s1RequestRuns.size();
                int totalTasks = 0;
                String prevTaskID = "EMPTY";
                String taskID = "";
                int totalRequestsInTask = 0;

                double taskE1Recall = 0.0;
                double taskE1Precision = 0.0;
                double taskE1RPrecision = 0.0;
                double taskE2Recall = 0.0;
                double taskE2Precision = 0.0;
                double taskE2RPrecision = 0.0;

                while (s1RequestsIterator.hasNext()) {
                    RequestRun s1RequestRun = s1RequestsIterator.next();
                    List<String> s1DocidsList = s1RequestRun.getDocids();
                    String requestID = s1RequestRun.getQueryID();
                    taskID = requestID.substring(0, 5);
                    if (!taskID.equals(prevTaskID) && !prevTaskID.equals("EMPTY")) {
                        ++totalTasks;
                        totalTaskE1Recall += (taskE1Recall / totalRequestsInTask);
                        totalTaskE1Precision += (taskE1Precision / totalRequestsInTask);
                        totalTaskE1RPrecision += (taskE1RPrecision / totalRequestsInTask);
                        totalTaskE2Recall += (taskE2Recall / totalRequestsInTask);
                        totalTaskE2Precision += (taskE2Precision / totalRequestsInTask);
                        totalTaskE2RPrecision += (taskE2RPrecision / totalRequestsInTask);
                        taskE1Recall = 0.0;
                        taskE1Precision = 0.0;
                        taskE1RPrecision = 0.0;
                        taskE2Recall = 0.0;
                        taskE2Precision = 0.0;
                        taskE2RPrecision = 0.0;
                        totalRequestsInTask = 0;
                    }
                    prevTaskID = taskID;
                    ++totalRequestsInTask;
                    Task t = findTask(taskID);
                    Request r = findRequest(t, requestID);
//                System.out.println("Task docs: " + t.taskDocList.size());
//                System.out.println("Request docs: " + r.reqDocList.size());
                    int taskDocsFound = 0;
                    int requestDocsFound = 0;
                    for (String s : t.taskDocList) {
                        if (s1DocidsList.contains(s)) {
                            ++taskDocsFound;
                        }
                    }
                    for (String s : r.reqDocList) {
                        if (s1DocidsList.contains(s)) {
                            ++requestDocsFound;
                        }
                    }
                /*
                System.out.println("Task docs:");
                System.out.println(t.taskDocList);
                System.out.println("Request docs:");
                System.out.println(r.reqDocList);
                System.out.printf("Task docs found: %d\n", taskDocsFound);
                System.out.printf("Request docs found: %d\n", requestDocsFound);
                 */

                    /* E1 is request level, E2 is task level */
                    double e1Recall = ((double) requestDocsFound / r.reqDocList.size()) * 100;
                    double e2Recall = ((double) taskDocsFound / t.taskDocList.size()) * 100;
                    double e1Precision = ((double) requestDocsFound / s1DocidsList.size()) * 100;
                    double e2Precision = ((double) taskDocsFound / s1DocidsList.size()) * 100;

                    String[] s1Docids = new String[s1DocidsList.size()];
                    s1Docids = s1DocidsList.toArray(s1Docids);

                    //System.out.printf("Top %d hits for E1:\n", r.reqDocList.size());
                    int score = 0;
                    for (int x = 0; x < r.reqDocList.size(); ++x) {
                        //System.out.println(s1Docids[x]);
                        if (r.reqDocList.contains(s1Docids[x])) {
                            score += 1;
                        }
                    }
                    double e1RPrecision = ((double) score / r.reqDocList.size()) * 100;

                    //System.out.printf("Top %d hits for E2:\n", t.taskDocList.size() );
                    score = 0;
                    for (int x = 0; x < t.taskDocList.size(); ++x) {
                        //System.out.println(s1Docids[x]);
                        if (t.taskDocList.contains(s1Docids[x])) {
                            score += 1;
                        }
                    }
                    double e2RPrecision = ((double) score / t.taskDocList.size()) * 100;

                    taskE1Precision += e1Precision;
                    taskE2Precision += e2Precision;
                    taskE1Recall += e1Recall;
                    taskE2Recall += e2Recall;
                    taskE1RPrecision += e1RPrecision;
                    taskE2RPrecision += e2RPrecision;

                    totalE1Precision += e1Precision;
                    totalE2Precision += e2Precision;
                    totalE1Recall += e1Recall;
                    totalE2Recall += e2Recall;
                    totalE1RPrecision += e1RPrecision;
                    totalE2RPrecision += e2RPrecision;
 
                /*
                System.out.println("Request " + requestID);
                System.out.println("  Evaluation Type 1 (Request level)");
                System.out.println("    Recall: " + e1Recall);
                System.out.println("    Precision: " + e1Precision);
                System.out.println("    RPrecision: " + e1RPrecision);
                System.out.println("  Evaluation Type 2 (Task level)");
                System.out.println("    Recall: " + e2Recall);
                System.out.println("    Precision: " + e2Precision);
                System.out.println("    RPrecision: " + e2RPrecision);
                */
                }
                if (!prevTaskID.equals("EMPTY")) {
                    ++totalTasks;
                    totalTaskE1Recall += (taskE1Recall / totalRequestsInTask);
                    totalTaskE1Precision += (taskE1Precision / totalRequestsInTask);
                    totalTaskE1RPrecision += (taskE1RPrecision / totalRequestsInTask);
                    totalTaskE2Recall += (taskE2Recall / totalRequestsInTask);
                    totalTaskE2Precision += (taskE2Precision / totalRequestsInTask);
                    totalTaskE2RPrecision += (taskE2RPrecision / totalRequestsInTask);
                }
                double microAvgE1Recall = totalE1Recall / numRequests;
                double microAvgE1Precision = totalE1Precision / numRequests;
                double microAvgE1RPrecision = totalE1RPrecision / numRequests;
                double microAvgE2Recall = totalE2Recall / numRequests;
                double microAvgE2Precision = totalE2Precision / numRequests;
                double microAvgE2RPrecision = totalE2RPrecision / numRequests;

                double macroAvgE1Recall = totalTaskE1Recall / totalTasks;
                double macroAvgE1Precision = totalTaskE1Precision / totalTasks;
                double macroAvgE1RPrecision = totalTaskE1RPrecision / totalTasks;
                double macroAvgE2Recall = totalTaskE2Recall / totalTasks;
                double macroAvgE2Precision = totalTaskE2Precision / totalTasks;
                double macroAvgE2RPrecision = totalTaskE2RPrecision / totalTasks;

                /* Do MACRO only */
//            csvWriter.append(solutionName);
//            csvWriter.append(",");
//            csvWriter.append("Request");
//            csvWriter.append(",");
//            csvWriter.append(Integer.toString(resultSetSize));
//            csvWriter.append(",");
//            /*
//            csvWriter.append("MICRO");
//            csvWriter.append(",");
//            */
//            csvWriter.append(String.format("%.2f",microAvgE1Recall));
//            csvWriter.append(",");
//            csvWriter.append(String.format("%.2f",microAvgE1Precision));
//            csvWriter.append(",");
//            csvWriter.append(String.format("%.2f",microAvgE1RPrecision));
//            csvWriter.append("\n");
//
//            csvWriter.append(solutionName);
//            csvWriter.append(",");
//            csvWriter.append("Task");
//            csvWriter.append(",");
//            csvWriter.append(Integer.toString(resultSetSize));
//            csvWriter.append(",");
//            /*
//            csvWriter.append("MICRO");
//            csvWriter.append(",");
//            */
//            csvWriter.append(String.format("%.2f",microAvgE2Recall));
//            csvWriter.append(",");
//            csvWriter.append(String.format("%.2f",microAvgE2Precision));
//            csvWriter.append(",");
//            csvWriter.append(String.format("%.2f",microAvgE2RPrecision));
//            csvWriter.append("\n");

                csvWriter.append(solutionName);
                csvWriter.append(",");
                csvWriter.append("Request");
                csvWriter.append(",");
                csvWriter.append(Integer.toString(resultSetSize));
                csvWriter.append(",");
            /*
            csvWriter.append("MACRO");
            csvWriter.append(",");
            */
                csvWriter.append(String.format("%.2f", macroAvgE1Recall));
                csvWriter.append(",");
                csvWriter.append(String.format("%.2f", macroAvgE1Precision));
                csvWriter.append(",");
                csvWriter.append(String.format("%.2f", macroAvgE1RPrecision));
                csvWriter.append("\n");

                csvWriter.append(solutionName);
                csvWriter.append(",");
                csvWriter.append("Task");
                csvWriter.append(",");
                csvWriter.append(Integer.toString(resultSetSize));
                csvWriter.append(",");
            /*
            csvWriter.append("MACRO");
            csvWriter.append(",");
            */
                csvWriter.append(String.format("%.2f", macroAvgE2Recall));
                csvWriter.append(",");
                csvWriter.append(String.format("%.2f", macroAvgE2Precision));
                csvWriter.append(",");
                csvWriter.append(String.format("%.2f", macroAvgE2RPrecision));
                csvWriter.append("\n");

                csvWriter.flush();
                System.out.println("****************************************");
                System.out.printf("solution: %s\n", solutionName);
                System.out.printf("atRank: %d\n", resultSetSize);
                System.out.printf("relevantDocSetUsed: %s\n", "Request Docs");
                System.out.printf("averagingType: %s\n", "MICRO");
                System.out.printf("avgRecall: %.2f\n", microAvgE1Recall);
                System.out.printf("avgPrecision: %.2f\n", microAvgE1Precision);
                System.out.printf("avgRPrecision: %.2f\n", microAvgE1RPrecision);

                System.out.printf("solution: %s\n", solutionName);
                System.out.printf("atRank: %d\n", resultSetSize);
                System.out.printf("relevantDocSetUsed: %s\n", "Task Docs");
                System.out.printf("averagingType: %s\n", "MICRO");
                System.out.printf("avgRecall: %.2f\n", microAvgE2Recall);
                System.out.printf("avgPrecision: %.2f\n", microAvgE2Precision);
                System.out.printf("avgRPrecision: %.2f\n", microAvgE2RPrecision);

                System.out.printf("solution: %s\n", solutionName);
                System.out.printf("atRank: %d\n", resultSetSize);
                System.out.printf("relevantDocSetUsed: %s\n", "Request Docs");
                System.out.printf("averagingType: %s\n", "MACRO");
                System.out.printf("avgRecall: %.2f\n", macroAvgE1Recall);
                System.out.printf("avgPrecision: %.2f\n", macroAvgE1Precision);
                System.out.printf("avgRPrecision: %.2f\n", macroAvgE1RPrecision);

                System.out.printf("solution: %s\n", solutionName);
                System.out.printf("atRank: %d\n", resultSetSize);
                System.out.printf("relevantDocSetUsed: %s\n", "Task Docs");
                System.out.printf("averagingType: %s\n", "MACRO");
                System.out.printf("avgRecall: %.2f\n", macroAvgE2Recall);
                System.out.printf("avgPrecision: %.2f\n", macroAvgE2Precision);
                System.out.printf("avgRPrecision: %.2f\n", macroAvgE2RPrecision);

                //            System.out.println((taskMatchCount + requestMatchCount)
//                               + " out of " + (totalRequestDocids + totalTaskDocids));
//            System.out.printf("%.0f%%\n",
//            ((taskMatchCount + requestMatchCount) * 1.0 /
//                    (totalRequestDocids + totalTaskDocids) * 100));
//            System.out.printf("%.0f%%\n",
//                    ((requestMatchCount) * 1.0 /
//                            (totalRequestDocids ) * 100));
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
        evaluator.process();
    }
}