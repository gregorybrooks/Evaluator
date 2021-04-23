package edu.umass.ciir;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.logging.*;
import java.util.stream.Stream;

public class Evaluator {

    String analyticTasksFile;
    String runFile;
    String qrelFile;
    String evaluationRequestLevelFileName;

    Evaluator(String analyticTasksFile, String runFile, String qrelFile, String evaluationRequestLevelFileName) {
        this.analyticTasksFile = analyticTasksFile;
        this.runFile = runFile;
        this.qrelFile = qrelFile;
        this.evaluationRequestLevelFileName = evaluationRequestLevelFileName;
    }

    private Map<String, RequestRun> requestRuns = new HashMap<String, RequestRun>();
    private final Map<RelevanceJudgmentKey,RelevanceJudgment> relevanceJudgments =
            new HashMap<RelevanceJudgmentKey, RelevanceJudgment>();
    private List<Task> tasks = new ArrayList<>();

    private static final Logger logger = Logger.getLogger("Evaluator");

    /**
     * Sets up logging for this program.
     */
    private void setupLogging() {
        String logFileName = "evaluator.log";
        configureLogger(logFileName);
    }

    /**
     * Configures the logger for this program.
     * @param logFileName Name to give the log file.
     */
    private void configureLogger(String logFileName) {
        SimpleFormatter formatterTxt;
        FileHandler fileTxt;
        try {
            // suppress the logging output to the console
            Logger rootLogger = Logger.getLogger("");
            Handler[] handlers = rootLogger.getHandlers();
            if (handlers[0] instanceof ConsoleHandler) {
                rootLogger.removeHandler(handlers[0]);
            }
            logger.setLevel(Level.INFO);
            fileTxt = new FileHandler(logFileName);
            // create a TXT formatter
            formatterTxt = new SimpleFormatter();
            fileTxt.setFormatter(formatterTxt);
            logger.addHandler(fileTxt);
        } catch (Exception cause) {
            throw new TasksRunnerException(cause);
        }
    }

    public class Hit {
        public String docid;
        public String score;
        Hit(String docid, String score) {
            this.docid = docid;
            this.score = score;
        }
        Hit(Hit other) {
            this.docid = other.docid;
            this.score = other.score;
        }
    }

    private String getOptionalValue(JSONObject t, String field) {
        if (t.containsKey(field)) {
            return (String) t.get(field);
        } else {
            return "";
        }
    }

    private void readTaskFile() {
        try {
            logger.info("Reading analytic tasks info file " + analyticTasksFile);

            Reader reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(analyticTasksFile)));
            JSONParser parser = new JSONParser();
            JSONArray tasksJSON = (JSONArray) parser.parse(reader);
            for (Object oTask : tasksJSON) {
                JSONObject t = (JSONObject) oTask;
                String taskNum = (String) t.get("task-num");
                String taskTitle = getOptionalValue(t, "task-title");
                String taskNarr = getOptionalValue(t, "task-narr");
                String taskStmt = "";
                JSONArray taskRequests = (JSONArray) t.get("requests");
                List<Request> requests = new ArrayList<>();
                for (Object o : taskRequests) {
                    JSONObject request = (JSONObject) o;
                    String reqText = getOptionalValue(request, "req-text");
                    String reqNum = (String) request.get("req-num");
                    requests.add(new Request(reqNum, reqText));
                }
                tasks.add(new Task(taskNum, taskTitle, taskStmt, taskNarr, requests));
            }
        } catch (Exception e) {
            throw new TasksRunnerException(e);
        }
    }

    private List<String> getRequestIDs() {
        List<String> list = new ArrayList<String>();
        for (Task t : tasks) {
            for (Request r : t.requests) {
                list.add(r.reqNum);
            }
        }
        return list;
    }

    public class RequestRun {
        public String requestID;
        public List<String> docids;
        public List<Hit> hits;

        RequestRun(String requestID, List<String> docids, List<Hit> hits) {
            this.requestID = requestID;
            this.docids = docids;
            this.hits = hits;
        }
    }

    private void readRunFile() {
        File f = new File(runFile);
        if (f.exists()) {
            logger.info("Opening run file " + runFile);
            List<String> docids = new ArrayList<>();
            List<Hit> hits = new ArrayList<>();
            try (BufferedReader br = new BufferedReader(new FileReader(runFile))) {
                String line;
                String prevQueryID = "NONE";
                int docidsAdded = 0;
                while ((line = br.readLine()) != null) {
                    String queryID = line.split("[ \t]+")[0];
                    if (!prevQueryID.equals(queryID)) {
                        if (!prevQueryID.equals("NONE")) {
                            // Clone the lists
                            List<String> cloned_docid_list
                                    = new ArrayList<String>(docids);
                            List<Hit> cloned_hit_list
                                    = new ArrayList<Hit>(hits);
                            RequestRun r = new RequestRun(prevQueryID, cloned_docid_list, cloned_hit_list);
                            requestRuns.put(prevQueryID, r);
                            docids.clear();
                            hits.clear();
                            docidsAdded = 0;
                        }
                    }
                    prevQueryID = queryID;
                    String[] tokens = line.split("[ \t]+");
                    String docid = tokens[2];
                    String score = tokens[4];
                    docids.add(docid);
                    Hit h = new Hit(docid, score);
                    hits.add(h);
                    ++docidsAdded;
                }
                if (!prevQueryID.equalsIgnoreCase("")) {
                    // Clone the lists
                    List<String> cloned_docid_list
                            = new ArrayList<String>(docids);
                    List<Hit> cloned_hit_list
                            = new ArrayList<Hit>(hits);
                    RequestRun r = new RequestRun(prevQueryID, cloned_docid_list, cloned_hit_list);
                    requestRuns.put(prevQueryID, r);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            logger.info("Run file requested does not exist: " + runFile);
        }
    }

    private class RelevanceJudgmentKey {
        String requestID;
        String docid;
        RelevanceJudgmentKey(String requestID, String docid) {
            this.requestID = requestID;
            this.docid = docid;
        }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RelevanceJudgmentKey oKey = (RelevanceJudgmentKey) o;
            if (!requestID.equals(oKey.requestID)) return false;
            return (docid.equals(oKey.docid));
        }
        @Override
        public int hashCode() {
            int result = requestID.hashCode();
            result = 31 * result + (docid.hashCode());
            return result;
        }
    }

    /**
     * Reads the relevance judgments file and creates relevanceJudgments.
     */
    private void readQRELFile() {
        File f = new File(qrelFile);
        if (f.exists()) {
            try {
            BufferedReader qrelReader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(qrelFile)));
            String line = qrelReader.readLine();
            while (line != null) {
                String[] tokens = line.split(" ");
                String requestID = tokens[0];
                String docid = tokens[1];
                String judgment = tokens[2];
                RelevanceJudgment j = new RelevanceJudgment(requestID, docid, "", "", judgment);
                RelevanceJudgmentKey jk = new RelevanceJudgmentKey(requestID,docid);
                relevanceJudgments.put(jk, j);
                line = qrelReader.readLine();
            }
            qrelReader.close();
            } catch (IOException e) {
                throw new TasksRunnerException(e);
            }
        }
    }

    /**
     * Returns a list of relevance judgment objects for this request, but only for those where
     * the judgment is relevant in some way (not "not relevant").
     * There might be multiple relevance judgment objects per request/docid.
     * @param requestID the request ID
     * @return a list of relevance judgment objects
     */
    private List<RelevanceJudgment> getPositiveRelevanceJudgments(String requestID) {
        List<RelevanceJudgment> judgments = new ArrayList<>();
        for (Map.Entry<RelevanceJudgmentKey, RelevanceJudgment> entry : relevanceJudgments.entrySet()) {
            RelevanceJudgmentKey k = entry.getKey();
            RelevanceJudgment j = entry.getValue();
            if (k.requestID.equals(requestID)) {
                if (j.judgment.isRelevant() ) { /* Don't include judgments of "not relevant" */
                    judgments.add(new RelevanceJudgment(j));
                }
            }
        }
        return judgments;
    }

    private List<String> getDocids(String requestID, int size) {
        if (requestRuns.containsKey(requestID)) {
            List<String> docids = requestRuns.get(requestID).docids;
            if (size < docids.size()) {
                return docids.subList(0, size);
            } else {
                return docids;
            }
        } else {
            return new ArrayList<String>();
        }
    }

    /**
     * Returns true if this request has any relevance judgments, else return false.
     * @param requestID the request ID
     * @return true if this request has any relevance judgments else false
     */
    private Boolean hasRelevanceJudgments(String requestID) {
        for (Map.Entry<RelevanceJudgmentKey, RelevanceJudgment> entry : relevanceJudgments.entrySet()) {
            RelevanceJudgmentKey k = entry.getKey();
            RelevanceJudgment j = entry.getValue();
            if (k.requestID.equals(requestID)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the relevance judgment integer value, mapped as MITRE maps them for the IR
     * nDCG calculation, for the given request and doc ID.
     * @param requestID the request ID
     * @param docid the doc ID
     * @return the mapped relevance judgment integer value
     */
    private int getRelevanceJudgmentValueWithMapping(String requestID, String docid) {
        RelevanceJudgmentKey jk = new RelevanceJudgmentKey(requestID,docid);
        RelevanceJudgment j = relevanceJudgments.get(jk);
        if (j == null) {
            return 0;  // for unjudged, assume not relevant
        } else {
            return j.getRelevanceJudgmentValueWithMapping();
        }
    }

    /**
     * Evaluates a run file, request level.
     */
    private void evaluate() {
        BetterEvaluator b = new BetterEvaluator();
        b.evaluate();
    }

    public class BetterEvaluator {
        /**
         * Calculates the normalized discounted cumulative gain
         * for this request and this ranked set of docs.
         *
         * @param requestID The request, with its relevance judgments available.
         * @param runDocids The ranked hits.
         * @return The calculated nDCG.
         */

        private Map<String, Double> stats = new TreeMap<>();

        /** This version computes nDCG to the depth equal to the number
         * of known relevant documents ("R"), instead of a hard cutoff of 10
         * as in his original version.
         *
         * @param requestID
         * @param runDocids
         * @return
         */
        private double calculatenDCG(String requestID, List<String> runDocids) {
            List<RelevanceJudgment> judgments = getPositiveRelevanceJudgments(requestID);
            int cutoff = judgments.size();
            /* Calculate the ideal discounted cumulative gain for this query */
            judgments.sort(Comparator.comparingInt(RelevanceJudgment::getRelevanceJudgmentValue)
                    .reversed());
            double iDCG = 0.0;
            int index = 1;
            for (RelevanceJudgment j : judgments) {
                if (index == 1) {
                    iDCG += j.getRelevanceJudgmentValueWithMapping();
                } else {
                    iDCG += (j.getRelevanceJudgmentValueWithMapping() / ((Math.log(index + 1) / Math.log(2))));
                }
                ++index;
            }
            /* Calculate discounted cumulative gain of the ranked hits */
            double DCG = 0.0;
            index = 1;
            for (String docid : runDocids) {
                if (index == 1) {
                    DCG += getRelevanceJudgmentValueWithMapping(requestID, docid);
                } else {
                    DCG += (getRelevanceJudgmentValueWithMapping(requestID, docid)
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
         * This version only calculates nDCG@R.
         * <p>
         * For averaging the evaluation results, we use the MICRO approach.
         *
         * @throws IOException
         * @throws InterruptedException
         */
        public void evaluate() {
            try {
                /* Create and open the output CSV file */
                FileWriter csvWriter = new FileWriter(evaluationRequestLevelFileName);
                /* Write the header line */
                csvWriter.append("Request");
                csvWriter.append(",");
                csvWriter.append("nDCG@R");
                csvWriter.append("\n");

                List<String> requestIDs = getRequestIDs();
                ListIterator<String> requestIDIterator = requestIDs.listIterator();

                int totalRequests = 0;
                double totalnDCG = 0.0;

                while (requestIDIterator.hasNext()) {
                    String requestID = requestIDIterator.next();
                    List<String> runDocids = getDocids(requestID, 1000);
                    /* If this solution did not provide a query for this request, skip it */
                    if (runDocids.size() == 0) {
                        continue;
                    }
                    /* If we have no relevance judgments for this request, skip it */
                    if (!hasRelevanceJudgments(requestID)) {
                        continue;
                    }

                    ++totalRequests;

                    double nDCG = calculatenDCG(requestID, runDocids);

                    totalnDCG += nDCG;

                    csvWriter.append(requestID);
                    csvWriter.append(",");
                    csvWriter.append(String.format("%.4f", nDCG));
                    csvWriter.append("\n");
                }

                double microAvgnDCG = totalnDCG / totalRequests;

                csvWriter.append("TOTAL");
                csvWriter.append(",");
                csvWriter.append(String.format("%.4f", microAvgnDCG));
                csvWriter.append("\n");

                csvWriter.close();
            } catch (Exception e) {
                throw new TasksRunnerException(e.getMessage());
            }
        }
    }

    /**
     * Processes the analytic tasks file: generates queries for the Tasks and Requests,
     * executes the queries, annotates hits with events.
     */
    private void process() {
        readTaskFile();
        readRunFile();
        readQRELFile();
        evaluate();
    }

    /**
     * Public entry point for this class.
     */
    public static void main (String[] args) {
        for (int x = 0; x < args.length; ++x) {
            System.out.println(x + ": " + args[x]);
        }
        if (args.length < 4) {
            System.out.println("evaluator: calculate nDCG@R as per BETTER");
            System.out.println("Usage: evaluator analytic-tasks-file run-file qrel-file output-file");
            System.exit(-1);
        }
        String analyticTasksFile = args[0];
        String runFile = args[1];
        String qrelFile = args[2];
        String evaluationRequestLevelFileName = args[3];
        Evaluator betterIR = new Evaluator(analyticTasksFile, runFile, qrelFile, evaluationRequestLevelFileName);

        betterIR.setupLogging();
        betterIR.process();
    }
}
