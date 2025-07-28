## Demo of entry missing in Continuous Query

It seems that in a very rare case, Apache Ignite's Continuous Query can miss an
entry when the cache is hammered with new records. This is a demo of such an
error. Also, the `sample-logs` dir has the logs from a run which ended up with
this error (the logs are huge, so cloning takes a while!)

The error was observed (with exactly the same codebase) on ARM Macs and
Intel-based Windows machines.

It was possible to reproduce this error only with Java 8 and Ignite 2.16.0.
Unfortunately, it doesn't mean the error is fixed in later versions of Ignite.
Say, **Java 21** with Ignite 2.16.0 runs fine, so it seems that the race
condition is not happening in Java 21. At the same time, it's not possible to
test the Java 8/Ignite 2.17 combination, since the latter requires Java 11. So
it could still be that the "right" race condition just did not hit in the later
version, while in fact it is still there and _will_ hit eventually in a
different environment (say, one where the cluster is spread across many
machines).

### The absolutely quickest start - just look at the logs

If you don't want to run the demo since it takes a while to catch the error,
then just open the logs from the `sample-logs` directory. Say, in `logs-A.zip`,
in the `2.log` (the log file for the instance running the query, see below the
`How it works` section), the `key-187` pops ups in the Ignite log lines, but
never comes up in either Continuous Query events or in its Initial Query.

Say, the line that looks suspicious for this key is:

```
DEBUG o.a.i.c.query - Skip duplicate continuous query message: CacheContinuousQueryEntry [evtType=CREATED, key=KeyCacheObjectImpl
```

... so it seems the entry has actually been received - but it never came up in
the Ignite log earlier??

The logs from the `sample-logs` dir were obtained on a Mac M1 with Java 8 and
Apache Ignite 2.16.0. **The error is very rare. It took 8 hours to catch the
error shown in logs-B.zip, although it could also pop up in just a few minutes
(as was observed on another occasion too).**

Again, the same error was observed (with exactly the same codebase) on ARM Macs
and Intel-based Windows machines.

### Quick Start

It will actually take a while for the script to catch the error, so it's not
that quick really. But starting it up *is* quick:

* Make sure that Java 8 is in the path (see the notes above why not a better
  Java), or set JAVA\_HOME to point to Java 8.
* Run `./run-test.sh` - it builds the code first, then runs the test case
  (below) over and over again until the expected error is caught.
* **Again, see above - it can take MANY hours for the error to happen.**
* If you finally see this line in the console, the error is caught:

  `06:51:24 - elapsed 07:45:56 - !!! Yay! Expected error has happened, check the logs (build/*.log) for details. !!!`

### How it works

The Java code is relatively simple:

* Each time the Main class runs, it adds an Ignite node to the cluster (where
  the 1st node establishes the cluster... i.e. the demo is self-sufficient)
* The 2nd node sets up a Continuous Query, the one that checks for missed
  entries.
* The 3rd node writes data to the cache — the data that the 2nd node listens to.
* (So don't add a 4th or other node... even on other machines in the same
  network)
* The 3rd node fills in the cache with 10\_000 records using `putAsync`.
* Once all data is written by the 3rd node, and it shows the log line
  saying (`Finished adding data`), the log in the 2nd node shows either:

  * `The test case did not fail this time, exiting with success` — that's
    happening in most cases, OR
  * `Key {} was not observed` — that's what can be seen in the log files from
    the `sample-logs` directory, and that's the error that this demo is about.

The script `run-test.sh` does the above repeatedly. The script is a bit tricky
in its structure, but essentially it builds the code, then it runs the test and
repeats it until the expected error is caught, or until any other unexpected
errors happen too many times.

As mentioned above, if the script ends with the line saying
`Expected error has happened` (which might take hours), then the error is
finally caught.
