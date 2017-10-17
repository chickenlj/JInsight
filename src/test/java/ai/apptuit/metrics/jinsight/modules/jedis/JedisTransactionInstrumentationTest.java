/*
 * Copyright 2017 Agilx, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ai.apptuit.metrics.jinsight.modules.jedis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import ai.apptuit.metrics.jinsight.RegistryService;
import ai.apptuit.metrics.jinsight.testing.CountTracker;
import ai.apptuit.metrics.jinsight.testing.CountTracker.Snapshot;
import com.codahale.metrics.MetricRegistry;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Response;
import redis.clients.jedis.Transaction;


/**
 * @author Rajiv Shivane
 */
public class JedisTransactionInstrumentationTest {

  private Map<String, Object> presetElements;
  private List<String> presetElementKeys;

  private Jedis jedis;
  private CountTracker execTracker;
  private CountTracker discardTracker;
  private CountTracker commandTracker;


  @Before
  public void setUp() throws Exception {
    MetricRegistry registry = RegistryService.getMetricRegistry();

    presetElements = IntStream.range(0, 1000).boxed()
        .collect(Collectors.toMap(i -> UUID.randomUUID().toString(), i -> i));
    presetElementKeys = new ArrayList<>(presetElements.keySet());

    String redisAddr = System.getProperty("redis.addr");

    jedis = new Jedis(new URI("redis://" + redisAddr));

    execTracker = new CountTracker(registry, JedisRuleHelper.TRANSACTIONS_EXEC_METRIC);
    execTracker.registerTimer();
    discardTracker = new CountTracker(registry, JedisRuleHelper.TRANSACTIONS_DISCARD_METRIC);
    discardTracker.registerTimer();
    commandTracker = new CountTracker(registry, JedisRuleHelper.COMMANDS_BASE_NAME, "command");
    commandTracker.registerTimer("sadd");
  }

  @After
  public void tearDown() throws Exception {
    jedis.close();
  }

  @Test
  public void testExec() throws Exception {
    int rnd = ThreadLocalRandom.current().nextInt(0, presetElements.size());
    String key = presetElementKeys.get(rnd);
    String value = String.valueOf(presetElements.get(key));

    Transaction transaction = jedis.multi();

    Snapshot snapshot = commandTracker.snapshot();
    Snapshot discardSnapshot = discardTracker.snapshot();
    Snapshot txsnapshot = execTracker.snapshot();
    txsnapshot.increment();
    Response<Long> added = transaction.sadd(key, value);
    transaction.exec();
    assertEquals(1, (long) added.get());
    txsnapshot.validate();
    snapshot.validate();
    discardSnapshot.validate();
  }


  @Test
  public void testDiscard() throws Exception {
    int rnd = ThreadLocalRandom.current().nextInt(0, presetElements.size());
    String key = presetElementKeys.get(rnd);
    String value = String.valueOf(presetElements.get(key));

    Transaction transaction = jedis.multi();

    Snapshot snapshot = commandTracker.snapshot();
    Snapshot discardSnapshot = discardTracker.snapshot();
    Snapshot txsnapshot = execTracker.snapshot();
    discardSnapshot.increment();
    Response<Long> added = transaction.sadd(key, value);
    transaction.discard();
    assertNull(jedis.get(key));
    txsnapshot.validate();
    snapshot.validate();
    discardSnapshot.validate();
  }


  @Test
  public void testCloseDiscards() throws Exception {
    int rnd = ThreadLocalRandom.current().nextInt(0, presetElements.size());
    String key = presetElementKeys.get(rnd);
    String value = String.valueOf(presetElements.get(key));

    Transaction transaction = jedis.multi();

    Snapshot snapshot = commandTracker.snapshot();
    Snapshot discardSnapshot = discardTracker.snapshot();
    Snapshot txsnapshot = execTracker.snapshot();
    discardSnapshot.increment();
    Response<Long> added = transaction.sadd(key, value);
    transaction.close();
    assertNull(jedis.get(key));
    txsnapshot.validate();
    snapshot.validate();
    discardSnapshot.validate();
  }

}
