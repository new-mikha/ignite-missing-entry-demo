package org.example;

import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.events.EventType;

public class Tools {

  public static int ENTRIES_COUNT = 10_000;

  public static IgniteConfiguration getIgniteConfiguration() {
    IgniteConfiguration igniteConfiguration = new IgniteConfiguration();

    igniteConfiguration.setIncludeEventTypes(EventType.EVT_NODE_JOINED,
      EventType.EVT_NODE_LEFT, EventType.EVT_NODE_FAILED);

    return igniteConfiguration;
  }

  public static CacheConfiguration<String, DataA> getCacheConfig() {
    CacheConfiguration<String, DataA> cacheCfg = new CacheConfiguration<>();

    cacheCfg.setName(DataA.class.getSimpleName());
    cacheCfg.setCacheMode(CacheMode.PARTITIONED);
    cacheCfg.setBackups(1);

    return cacheCfg;
  }

  public static void spinWait(long micros) {
    long start = System.nanoTime();

    //noinspection StatementWithEmptyBody
    while (System.nanoTime() < start + micros * 1_000) {
      //
    }
  }
}
