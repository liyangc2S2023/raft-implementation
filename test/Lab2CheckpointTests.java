package test;

import test.util.*;
import java.util.HashMap;
import java.util.Map;

/** Runs all Checkpoint tests for lab 2 Raft implementation.

    <p>
    Tests performed are:
    <ul>
    <li>{@link test.raft.TestCheckpoint_Setup}</li>
    <li>{@link test.raft.TestCheckpoint_InitialElection}</li>
    <li>{@link test.raft.TestCheckpoint_ReElection}</li>
    </ul>
 */
public class Lab2CheckpointTests {

    /** number of times to run each test */
    private static int runsOfEachTest = 1;

    /** Runs the tests.

        @param arguments Ignored.
     */
    public static void main(String[] arguments) {

        // Create the test list, the series object, and run the test series.
        @SuppressWarnings("unchecked")
        Class<? extends Test>[] tests = new Class[] {
            test.raft.TestCheckpoint_Setup.class,
            test.raft.TestCheckpoint_InitialElection.class,
            test.raft.TestCheckpoint_ReElection.class
        };

        Map<String, Integer> points = new HashMap<>();

        points.put("test.raft.TestCheckpoint_Setup", 10);
        points.put("test.raft.TestCheckpoint_InitialElection", 25);
        points.put("test.raft.TestCheckpoint_ReElection", 30);
        
        Series series = new Series(tests, runsOfEachTest);
        SeriesReport report = series.run(180, System.out);

        // Print the report and exit with an appropriate exit status.
        report.print(System.out, points, runsOfEachTest);
        System.exit(report.successful() ? 0 : 2);
    }
}

