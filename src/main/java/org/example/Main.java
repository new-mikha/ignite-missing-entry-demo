package org.example;

import static org.example.Tools.getIgniteConfiguration;

import java.util.Arrays;
import java.util.Collection;
import java.util.Timer;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteAtomicLong;
import org.apache.ignite.Ignition;
import org.apache.ignite.cluster.BaselineNode;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.events.Event;
import org.apache.ignite.events.EventType;
import org.apache.ignite.lang.IgnitePredicate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Main {

  private static final Logger LOG = LogManager.getLogger();

  public static void main(String[] args) {
    try {
      LOG.info("Starting up, the args are: {}, java version is: {} . . .", String.join(" ", args),
        System.getProperty("java.version"));

      if (Arrays.asList(args).contains("--set-timeout")) {
        // Emergency exit after 2 minutes, to prevent stalling the test case:
        scheduleExitByTimeout();
      }

      Ignite ignite = startIgnite();

      int nodesCount = getNodesCount(ignite);

      LOG.info("Ignite started, current number of nodes: {}", nodesCount);

      if (nodesCount == 2)
        startListener(ignite);

      if (nodesCount == 3)
        startWriter(ignite);
    } catch (Throwable err) {
      LOG.error("Error in main", err);
      System.exit(1);
    }
  }

  private static void scheduleExitByTimeout() {
    // Schedule emergency application exit after 2 minutes:
    new Timer().schedule(new java.util.TimerTask() {
      @Override
      public void run() {
        LOG.info("Exiting application after 2 minutes.");
        System.exit(1);
      }
    }, 2 * 60 * 1000);

    LOG.info("Scheduled emergency application exit after 2 minutes.");
  }

  private static Ignite startIgnite() {
    IgniteConfiguration cfg = getIgniteConfiguration();

    Ignite ignite = Ignition.getOrStart(cfg);

    ignite.events().localListen((IgnitePredicate<Event>)evt -> {
        LOG.info("{}, current number of nodes: {}", evt.name(),
          getNodesCount(ignite));
        return true;

      }, EventType.EVT_NODE_JOINED, EventType.EVT_NODE_LEFT,
      EventType.EVT_NODE_FAILED);

    LOG.info("This node is started, id: {}", ignite.cluster().localNode().id());

    return ignite;
  }

  private static int getNodesCount(Ignite ignite) {
    Collection<BaselineNode> baselineNodes =
      ignite.cluster().currentBaselineTopology();

    if (baselineNodes == null)
      return 0;

    return baselineNodes.size();
  }

  private static void startListener(Ignite ignite) {
    IgniteAtomicLong igniteAtomicLong =
      ignite.atomicLong("sow-subscribe-listener", 0, true);

    if (!igniteAtomicLong.compareAndSet(0, 1))
      throw new RuntimeException(
        "Listener has already started. Restart the whole cluster??");

    new DemoListener(ignite).start();

    LOG.info("Created the listener.");
  }

  private static void startWriter(Ignite ignite) {
    IgniteAtomicLong igniteAtomicLong =
      ignite.atomicLong("sow-subscribe-writer", 0, true);

    if (!igniteAtomicLong.compareAndSet(0, 1))
      throw new RuntimeException(
        "Writer has already started. Restart the whole cluster??");

    new DemoWriter(ignite).start();

    LOG.info("Created the writer.");
  }
}
