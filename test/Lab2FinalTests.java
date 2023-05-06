package test;

import test.util.*;
import java.util.HashMap;
import java.util.Map;

/** Runs all Final tests for lab 2 Raft implementation.

    <p>
    Tests performed are:
    <ul>
    <li>{@link test.raft.TestCheckpoint_Setup}</li>
    <li>{@link test.raft.TestCheckpoint_InitialElection}</li>
    <li>{@link test.raft.TestCheckpoint_ReElection}</li>
    <li>{@link test.raft.TestFinal_BasicAgree}</li>
    <li>{@link test.raft.TestFinal_FailAgree}</li>
    <li>{@link test.raft.TestFinal_FailNoAgree}</li>
    <li>{@link test.raft.TestFinal_Rejoin}</li>
    <li>{@link test.raft.TestFinal_Backup}</li>
    <li>{@link test.raft.TestFinal_Count}</li>
    </ul>
 */
public class Lab2FinalTests {

    /** number of times to run each test */
    private static int runsOfEachTest = 8;

    /** Runs the tests.

        @param arguments Ignored.
     */
    public static void main(String[] arguments) {

        // Create the test list, the series object, and run the test series.
        @SuppressWarnings("unchecked")
        Class<? extends Test>[] tests = new Class[] {
            test.raft.TestCheckpoint_Setup.class,
            test.raft.TestCheckpoint_InitialElection.class,
            test.raft.TestCheckpoint_ReElection.class,
            test.raft.TestFinal_BasicAgree.class,
            test.raft.TestFinal_FailAgree.class,
            test.raft.TestFinal_FailNoAgree.class,
            test.raft.TestFinal_Rejoin.class,
            test.raft.TestFinal_Backup.class,
            test.raft.TestFinal_Count.class
        };

        Map<String, Integer> points = new HashMap<>();
   
        points.put("test.raft.TestCheckpoint_Setup", 10);
        points.put("test.raft.TestCheckpoint_InitialElection", 25);
        points.put("test.raft.TestCheckpoint_ReElection", 30);
        points.put("test.raft.TestFinal_BasicAgree", 40);
        points.put("test.raft.TestFinal_FailAgree", 40);
        points.put("test.raft.TestFinal_FailNoAgree", 50);
        points.put("test.raft.TestFinal_Rejoin", 50);
        points.put("test.raft.TestFinal_Backup", 50);
        points.put("test.raft.TestFinal_Count", 25);
        
        Series series = new Series(tests, runsOfEachTest);
        SeriesReport report = series.run(180, System.out);

        // Print the report and exit with an appropriate exit status.
        report.print(System.out, points, runsOfEachTest);
        System.exit(report.successful() ? 0 : 2);
    }
}
