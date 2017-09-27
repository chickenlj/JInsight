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

package ai.apptuit.metrics.jinsight.modules.servlet;

import static ai.apptuit.metrics.jinsight.modules.servlet.ServletRuleHelper.ROOT_CONTEXT_PATH;
import static ai.apptuit.metrics.jinsight.modules.servlet.ServletRuleHelper.TOMCAT_METRIC_PREFIX;
import static org.junit.Assert.assertEquals;

import ai.apptuit.metrics.dropwizard.TagEncodedMetricName;
import ai.apptuit.metrics.jinsight.RegistryService;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;


/**
 * @author Rajiv Shivane
 */
public class TomcatFilterInstrumentationTest {

  private static final int SERVER_PORT = 9898;

  private MetricRegistry registry;
  private Tomcat tomcatServer;

  @Before
  public void setup() throws Exception {
    registry = RegistryService.getMetricRegistry();

    System.out.println("Tomcat [Configuring]");
    tomcatServer = new Tomcat();
    tomcatServer.setPort(SERVER_PORT);

    Path tempDirectory = Files.createTempDirectory(this.getClass().getSimpleName());
    File baseDir = new File(tempDirectory.toFile(), "tomcat");
    tomcatServer.setBaseDir(baseDir.getAbsolutePath());

    File applicationDir = new File(baseDir + "/webapps", "/ROOT");
    if (!applicationDir.exists()) {
      applicationDir.mkdirs();
    }

    Context appContext = tomcatServer.addWebapp("", applicationDir.getAbsolutePath());
    PingPongServlet servlet = new PingPongServlet();
    Tomcat.addServlet(appContext, servlet.getClass().getSimpleName(), servlet);
    appContext.addServletMappingDecoded(PingPongServlet.PATH, servlet.getClass().getSimpleName());

    System.out.println("Tomcat [Starting]");
    tomcatServer.start();
    System.out.println("Tomcat [Started]");
  }

  @After
  public void destroy() throws Exception {
    System.out.println("Tomcat [Stopping]");
    tomcatServer.stop();
    System.out.println("Tomcat [Stopped]");
  }

  @Test
  public void testPingPong() throws IOException {
    TagEncodedMetricName metricName = TagEncodedMetricName.decode(TOMCAT_METRIC_PREFIX)
        .submetric("requests").withTags("context", ROOT_CONTEXT_PATH, "method", "GET");
    long expectedCount = getTimerCount(metricName) + 1;

    URL url = pathToURL(PingPongServlet.PATH);
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.connect();

    assertEquals(HttpServletResponse.SC_OK, connection.getResponseCode());
    assertEquals(PingPongServlet.PONG,
        new Scanner(connection.getInputStream()).useDelimiter("\0").next());

    assertEquals(expectedCount, getTimerCount(metricName));
  }

  private URL pathToURL(String path) throws MalformedURLException {
    return new URL("http://localhost:" + SERVER_PORT + path);
  }


  private long getTimerCount(TagEncodedMetricName name) {
    Timer timer = registry.getTimers().get(name.toString());
    return timer != null ? timer.getCount() : 0;
  }

  private static class PingPongServlet extends HttpServlet {

    static final String PONG = "pong";
    static final String PATH = "/ping";

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {
      response.setStatus(HttpServletResponse.SC_OK);
      response.getWriter().append(PONG);
    }
  }
}