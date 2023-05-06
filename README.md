## Lab 2 - Raft, Java Version

This file details the contents of the initial Lab 2 code repository and how to use it.

### Getting started

If you're using the same programming language for this lab as the previous one, the look and feel of this
lab should look familiar, and your environment shouldn't need any changes.


### Initial repository contents

The top-level directory (called `lab2-java` here) of the initial starter-code repository includes four things:
* This `README.md` file
* The Lab 2 `Makefile`, described in detail later
* The `test` directory that contains the Lab 2 auto-grader, which you should not modify
* The `remote` library directory, where you should copy your `remote` package from lab 1
* The `raft` source directory, which is where all your work will be done

Visually, this looks roughly like the following, with the `test` directory compressed for clarity:
```
\---lab2-java
    +---remote
    +---raft
    |   +---RaftInterface.java
    |   +---RaftPeer.java
    |   +---StatusReport.java
    |   \---package-info.java
    +---test
    |   +---raft
    |   |   +---Controller.java
    |   |   +---TestCheckpoint_*.java  (3x in total)
    |   |   +---TestFinal_*.java       (6x in total)
    |   |   \---package-info.java
    |   +---util     [contains generic test suite, same as lab 0-1]
    |   +---Lab2CheckpointTests.java
    |   +---Lab2FinalTests.java
    |   \---package-info.java
    +---Makefile
    \---README.md
```
The details of each of these will hopefully become clear after reading the rest of this file.


### Implementing Raft

The first things you'll do in this lab is to copy your `remote` library code from Lab 1 into the corresponding
directory in your Lab 2 repository.  It is ok if you need to make changes to your `remote` library for Lab 2.

The `raft` package initially includes a rough outline of what you are required to implement, mainly based on the
Raft paper but also adhering to the needs of our test suite (see below).  Your primary task is to complete the
implementation of the Raft protocol according to the specifications given in the Canvas assignment.  You are free
to create additional source files within the `raft` package as needed, and you can use whatever Java classes and 
data structures you desire to implement the protocol.  However, you cannot change the test suite, so the provided
interactions between `raft` and `test` must be maintained.  You are welcome (and encouraged) to read the `test` 
code to see how the tests work and what they are testing.


### Understanding the Test Suite

As in Labs 0 and 1, the test suite includes generic capabilities in `test.util` and specific tests for the `raft`
package in `test.raft`.  These tests are called by the `test/Lab2CheckpointTests.java` and `test/Lab2FinalTests.java`
files.


### Testing your Raft Implementation

Once you're at the point where you want to run any of the provided tests, you can use the provided `make` rules. To run
the set of Checkpoint tests, execute
```
make checkpoint
```
from the main working directory of the lab. Similarly, to run the Final tests (which include the Checkpoint tests), execute
```
make test
```
You can also run subsets of tests by commenting out test Classes in the Lab 2 test files.  All of the tests in `test.raft`
are done by creating a `Controller`, which is a part of the test suite and should operate as given; you can change it during
development, but you will need to pass tests with the original version provided.  You are always welcome to create your own
test classes and application code as needed, but these will not be used by the auto-grader.  In order for the auto-grader 
to function correctly, your `RaftNode` implementation in the `raft` package will need to support several specific methods,
both those required by the Raft algorithm itself and additional methods required for the `Controller` to interact with the
`RaftNode`.  The details of these methods are detailed in the comments in the starter code.

You are welcome to create additional `make` rules in the Makefile, but we ask that you keep the existing `test` and `checkpoint`
rules, as we will use them for lab grading.


### Generating documentation

As done previously, you can use the Javadocs utility to create browseable documentation for the `raft` and `test` packages
using the `docs` and `docs-test` Makefile rules, and your final submission should include the `doc` folder created by `make docs`.


### Questions?

If there is any part of the initial repository, environment setup, lab requirements, or anything else, please do not hesitate
to ask.  We're here to help!

