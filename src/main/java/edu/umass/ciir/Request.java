package edu.umass.ciir;

import java.util.logging.Logger;

/**
 * Represents a specific analytic request within the larger analytic task.
 */
public class Request {
    private static final Logger logger = Logger.getLogger("Evaluator");
    String reqNum;
    String reqText;

    Request(String reqNum, String reqText) {
        this.reqNum = reqNum;
        this.reqText = reqText;
    }
    /**
     * Copy constructor (deep copy)
     * @param otherRequest The Request to make a copy of.
     */
    Request(Request otherRequest) {
        this.reqNum = otherRequest.reqNum;
        this.reqText = otherRequest.reqText;
    }

}
