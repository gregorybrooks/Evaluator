import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.util.*;

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
            "CLEAR-1-TEST"
            /*,
            "CLEAR-2-TEST",
            "CLEAR-3-TEST",
            "CLEAR-4-TEST",
            "CLEAR-5-TEST",
            "CLEAR-6-TEST",
            "BBN-1",
            "JHU-1",
            "BROWN-1"
            */));

    /**
     * The file that contains the task and request definitions.
     */
    private static final String tasksAndRequestsFile = queriesFilesDirectory + "dry-run-topics.auto.json";

    /**
     * The tasks in the tasksAndRequestsFile converted into a Map of Task objects.
     */
    private final Map<String,Task> tasks = new HashMap<>();

    /**
     * The solution results after being converted to Solution objects.
     */
    private final Map<String,Solution> solutionResults = new HashMap<>();

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
        String fileName = resultsFilesDirectory + "dry-run-topics.auto." + solution + ".out";
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
                docids.add(line.split("[ \t]+")[2]); // doc ID is 3rd field
                ++docidsAdded;
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

    /**
     */
    private void process() throws IOException, InterruptedException, ParseException {
        loadTasks();
        loadSolutionResults();

        List<String> solutions = new ArrayList<String>(Arrays.asList(
                "CLEAR-BASE-TEST",
                "CLEAR-1-TEST"
                /*,
                "CLEAR-2-TEST",
                "CLEAR-3-TEST",
                "CLEAR-4-TEST",
                "CLEAR-5-TEST",
                "CLEAR-6-TEST",
                "BBN-1",
                "JHU-1",
                "BROWN-1"
                */));


        for (String solutionName : solutions) {
            Solution s1 = solutionResults.get(solutionName);
            System.out.println("Evaluating solution " + s1.getName());
            List<RequestRun> s1RequestRuns = s1.getRequestRuns();
            ListIterator<RequestRun> s1RequestsIterator = s1RequestRuns.listIterator();
            int requestMatchCount = 0;
            int taskMatchCount = 0;
            int totalTaskDocids = 0;
            int totalRequestDocids = 0;
            int numRequests = s1RequestRuns.size();
            while (s1RequestsIterator.hasNext()) {
                RequestRun s1RequestRun = s1RequestsIterator.next();
                List<String> s1DocidsList = s1RequestRun.getDocids();
                String requestID = s1RequestRun.getQueryID();
                Task t = findTask(requestID.substring(0, 5));
                Request r = findRequest(t, requestID);
                for (String s : t.taskDocList) {
                    ++totalTaskDocids;
                    if (s1DocidsList.contains(s)) {
                        ++taskMatchCount;
                    }
                }
                for (String s : r.reqDocList) {
                    ++totalRequestDocids;
                    if (s1DocidsList.contains(s)) {
                        ++requestMatchCount;
                    }
                }
            }
            System.out.println((taskMatchCount + requestMatchCount)
                                + " out of " + (totalRequestDocids + totalTaskDocids));
        }
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
