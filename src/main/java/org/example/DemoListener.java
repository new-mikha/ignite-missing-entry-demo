package org.example;

import static org.example.Tools.ENTRIES_COUNT;
import static org.example.Tools.getCacheConfig;
import static org.example.Tools.spinWait;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.cache.Cache;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.CachePeekMode;
import org.apache.ignite.cache.query.ContinuousQuery;
import org.apache.ignite.cache.query.QueryCursor;
import org.apache.ignite.cache.query.ScanQuery;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DemoListener {
  private static final Logger LOG = LogManager.getLogger();

  private final Ignite ignite;

  public DemoListener(Ignite ignite) {
    this.ignite = ignite;
  }

  public void start() {
    new Thread(this::run, "LSTNR").start();
  }

  private void run() {
    try {
      CacheConfiguration<String, DataA> cacheConfig = getCacheConfig();
      IgniteCache<String, DataA> cache = ignite.getOrCreateCache(cacheConfig);

      // Wait until the cache got some of the initial data:
      while (cache.size(CachePeekMode.PRIMARY) < 150)
        spinWait(1_000);

      LOG.info("Running the query . . .");

      ContinuousQuery<String, DataA> continuousQuery = new ContinuousQuery<>();
      continuousQuery.setLocal(false);

      ScanQuery<String, DataA> initialQuery = new ScanQuery<>();
      initialQuery.setLocal(false);
      continuousQuery.setInitialQuery(initialQuery);

      Set<String> observedKeys = ConcurrentHashMap.newKeySet();

      continuousQuery.setLocalListener(events -> //
        events.forEach(event -> {
          observedKeys.add(event.getKey());
        }) //
      );

      QueryCursor<Cache.Entry<String, DataA>> cursor =
        cache.query(continuousQuery);

      for (Cache.Entry<String, DataA> entry : cursor)
        observedKeys.add(entry.getKey());

      // Wait not more than 20 seconds for the listener to observe all entries:
      for (int i = 0; i < 20; i++) {
        Thread.sleep(1_000);
        if (observedKeys.size() == ENTRIES_COUNT)
          break;
      }

      int observedKeysCount = observedKeys.size();

      LOG.info("Observed {} keys, cache size is {}", observedKeysCount,
        cache.size(CachePeekMode.PRIMARY));

      if (observedKeysCount == ENTRIES_COUNT) {
        LOG.info("The test case did not fail this time, exiting with success.");
        System.exit(0);
      }

      // At this point, at least one of the keys is missing.
      for (int i = 0; i < ENTRIES_COUNT; i++) {
        String key = "key-" + i;
        if (observedKeys.contains(key))
          continue;

        LOG.error("Key {} was not observed", key);
      }

      System.exit(32);
    } catch (Throwable err) {
      LOG.error("Error", err);
    }
  }
}
