package org.example;

import static org.example.Tools.ENTRIES_COUNT;
import static org.example.Tools.getCacheConfig;

import java.util.Base64;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.CachePeekMode;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DemoWriter {

  private static final Logger LOG = LogManager.getLogger();

  private final Ignite ignite;

  public DemoWriter(Ignite ignite) {
    this.ignite = ignite;
  }

  public void start() {
    new Thread(this::run, "WRTR").start();
  }

  private void run() {
    try {
      CacheConfiguration<String, DataA> cacheConfig = getCacheConfig();
      IgniteCache<String, DataA> cache = ignite.getOrCreateCache(cacheConfig);

      if (cache.size(CachePeekMode.PRIMARY) > 0) {
        LOG.error("Cache is not empty, expected to be empty for the writer "
          + "to start. Restart the whole cluster?");
        System.exit(1);
      }

      byte[] bytes = new byte[1024];
      CountDownLatch latch = new CountDownLatch(ENTRIES_COUNT);
      Set<String> keys = ConcurrentHashMap.newKeySet();

      for (int i = 0; i < ENTRIES_COUNT; i++) {
        randomiseBytes(i, bytes);
        String data = Base64.getEncoder().encodeToString(bytes);

        String key = "key-" + i;
        DataA value = new DataA(data, i);
        keys.add(key);

        cache.putAsync(key, value).listen($ -> {
          keys.remove(key);
          latch.countDown();
        });
      }

      LOG.info("Finished adding data");

      reportRemainingKeys(latch, keys);
    } catch (Throwable err) {
      LOG.error("Error", err);
      System.exit(1);
    }
  }

  private static void reportRemainingKeys(CountDownLatch latch,
    Set<String> keys)
  {
    try {
      if (latch.await(10_000, TimeUnit.MILLISECONDS)) {
        LOG.info("All putAsync operations completed successfully");
        return;
      }

      while (!keys.isEmpty()) {
        LOG.info("The remaining keys are: {}", String.join(",", keys));
        Thread.sleep(1_000);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.error("Interrupted while waiting for putAsync operations to complete",
        e);
    }
  }

  private static void randomiseBytes(long seed, byte[] bytes) {
    for (int i = 0; i < bytes.length; i++)
      bytes[i] = (byte)(i ^ seed);
  }
}
