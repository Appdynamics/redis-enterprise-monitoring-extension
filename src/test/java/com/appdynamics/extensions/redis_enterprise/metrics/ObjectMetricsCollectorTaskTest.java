package com.appdynamics.extensions.redis_enterprise.metrics;

import com.appdynamics.extensions.ABaseMonitor;
import com.appdynamics.extensions.MetricWriteHelper;
import com.appdynamics.extensions.conf.MonitorContext;
import com.appdynamics.extensions.conf.MonitorContextConfiguration;
import com.appdynamics.extensions.executorservice.MonitorExecutorService;
import com.appdynamics.extensions.http.HttpClientUtils;
import com.appdynamics.extensions.metrics.MetricCharSequenceReplacer;
import com.appdynamics.extensions.redis_enterprise.config.Metric;
import com.appdynamics.extensions.redis_enterprise.config.Stat;
import com.appdynamics.extensions.util.MetricPathUtils;
import com.appdynamics.extensions.yml.YmlReader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Phaser;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * @author: {Vishaka Sekar} on {2019-08-06}
 */
@RunWith(PowerMockRunner.class)
@PowerMockIgnore("javax.net.ssl.*")
@PrepareForTest({HttpClientUtils.class})
public class ObjectMetricsCollectorTaskTest {

    private MonitorContextConfiguration configuration;
    private MetricWriteHelper metricWriteHelper;
    private Phaser phaser  = new Phaser();
    private String metricPrefix =  "Custom Metrics|Redis Enterprise";
    private ArgumentCaptor<List> pathCaptor = ArgumentCaptor.forClass(List.class);

    @Before
    public void setUp(){

        configuration = mock(MonitorContextConfiguration.class);
        configuration.setConfigYml("src/test/resources/config.yml");
        metricWriteHelper = mock(MetricWriteHelper.class);
        Map<String, ?> conf = YmlReader.readFromFileAsMap(new File("src/test/resources/config.yml"));
        ABaseMonitor baseMonitor = mock(ABaseMonitor.class);
        MonitorContext context = mock(MonitorContext.class);
        Mockito.when(baseMonitor.getContextConfiguration()).thenReturn(configuration);
        Mockito.when(baseMonitor.getContextConfiguration().getContext()).thenReturn(context);
        MetricPathUtils.registerMetricCharSequenceReplacer(baseMonitor);
        MetricCharSequenceReplacer replacer = MetricCharSequenceReplacer.createInstance(conf);
        Mockito.when(context.getMetricCharSequenceReplacer()).thenReturn(replacer);
        Mockito.when(configuration.getMetricPrefix()).thenReturn(metricPrefix);
        MonitorExecutorService executorService = mock(MonitorExecutorService.class);
        when(configuration.getContext().getExecutorService()).thenReturn(executorService);
        Mockito.doNothing().when(executorService).execute(any(), any());
        phaser.register();
    }

    @Test
    public void whenFetchAllObjectsThenTestObjectMetricCollection(){

        Stat stat = new Stat();
        stat.setNameElement("name");
        stat.setIdElement("uid");
        stat.setStatsUrl("/v1/bdbs/stats/last/");
        stat.setUrl("/v1/bdbs/");
        PowerMockito.mockStatic(HttpClientUtils.class);
        when(HttpClientUtils.getResponseAsJson(any(CloseableHttpClient.class), anyString(), any(Class.class))).thenAnswer(
                new Answer() {
                    public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                        ObjectMapper mapper = new ObjectMapper();
                        String url = (String) invocationOnMock.getArguments()[1];
                        File file = null;
                        if(url.contains("bdbs")) {
                            file = new File("src/test/resources/objects.json");
                        }
                        JsonNode objectNode = mapper.readValue(file, JsonNode.class);
                        return objectNode;
                    }
                });

        com.appdynamics.extensions.redis_enterprise.config.Metric[] childMetrics = new Metric[1];
        com.appdynamics.extensions.redis_enterprise.config.Metric childMetric = new com.appdynamics.extensions.redis_enterprise.config.Metric();
        childMetric.setAttr("metric1");
        childMetric.setAlias("metric1");
        childMetrics[0] = childMetric;

        stat.setMetric(childMetrics);
        stat.setType("database");
        List<String> objectNames = new ArrayList<>();
        objectNames.add(".*");

        ObjectMetricsCollectorTask objectMetricsCollectorTask = new ObjectMetricsCollectorTask("displayname", "localhost:8080", objectNames, stat, configuration, metricWriteHelper, phaser );
        objectMetricsCollectorTask.run();

        verify(metricWriteHelper, times(1)).transformAndPrintMetrics(pathCaptor.capture());
        List<Metric> objectMetricList = pathCaptor.getAllValues().get(0);
        Assert.assertTrue(objectMetricList.size() == 3);
    }

    @Test
    public void whenSingleRegexThenTestObjectMetricCollection(){

        Stat stat = new Stat();
        stat.setNameElement("name");
        stat.setIdElement("uid");
        stat.setStatsUrl("/v1/bdbs/stats/last/");
        stat.setUrl("/v1/bdbs/");
        PowerMockito.mockStatic(HttpClientUtils.class);
        when(HttpClientUtils.getResponseAsJson(any(CloseableHttpClient.class), anyString(), any(Class.class))).thenAnswer(
                new Answer() {
                    public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                        ObjectMapper mapper = new ObjectMapper();
                        String url = (String) invocationOnMock.getArguments()[1];
                        File file = null;
                        if(url.contains("/v1/bdbs/stats/last")) {
                            file = new File("src/test/resources/bdbs-stats.json");
                        }
                        else if(url.contains("/v1/bdbs/")) {
                            file = new File("src/test/resources/objects.json");
                        }
                        JsonNode objectNode = mapper.readValue(file, JsonNode.class);
                        return objectNode;
                    }
                });

        com.appdynamics.extensions.redis_enterprise.config.Metric[] childMetrics = new Metric[1];
        com.appdynamics.extensions.redis_enterprise.config.Metric childMetric = new com.appdynamics.extensions.redis_enterprise.config.Metric();
        childMetric.setAttr("no_of_keys");
        childMetric.setAlias("no_of_keys");
        childMetrics[0] = childMetric;

        stat.setMetric(childMetrics);
        stat.setType("database");
        List<String> objectNames = new ArrayList<>();
        objectNames.add("redis.*");

        ObjectMetricsCollectorTask objectMetricsCollectorTask = new ObjectMetricsCollectorTask("displayname", "localhost:8080", objectNames, stat, configuration, metricWriteHelper, phaser );
        objectMetricsCollectorTask.run();

        verify(metricWriteHelper, times(1)).transformAndPrintMetrics(pathCaptor.capture());
        List<com.appdynamics.extensions.metrics.Metric> objectMetricList = pathCaptor.getAllValues().get(0);
        Assert.assertEquals(1,  objectMetricList.size());
        Assert.assertEquals("Custom Metrics|Redis Enterprise|displayname|database|redis-test|Status", objectMetricList.get(0).getMetricPath());
    }

    @Test
    public void whenMultipleRegexPatternsThenTestObjectMetricCollection(){

        Stat stat = new Stat();
        stat.setNameElement("name");
        stat.setIdElement("uid");
        stat.setStatsUrl("/v1/bdbs/stats/last/");
        stat.setUrl("/v1/bdbs/");
        PowerMockito.mockStatic(HttpClientUtils.class);
        when(HttpClientUtils.getResponseAsJson(any(CloseableHttpClient.class), anyString(), any(Class.class))).thenAnswer(
                new Answer() {
                    public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                        ObjectMapper mapper = new ObjectMapper();
                        String url = (String) invocationOnMock.getArguments()[1];
                        File file = null;
                        if(url.contains("/v1/bdbs/stats/last")) {
                            file = new File("src/test/resources/bdbs-stats.json");
                        }
                        else if(url.contains("bdbs")) {
                            file = new File("src/test/resources/objects.json");
                        }
                        JsonNode objectNode = mapper.readValue(file, JsonNode.class);
                        return objectNode;
                    }
                });

        com.appdynamics.extensions.redis_enterprise.config.Metric[] childMetrics = new Metric[1];
        com.appdynamics.extensions.redis_enterprise.config.Metric childMetric = new com.appdynamics.extensions.redis_enterprise.config.Metric();
        childMetric.setAttr("metric1");
        childMetric.setAlias("metric1");
        childMetrics[0] = childMetric;

        stat.setMetric(childMetrics);
        stat.setType("database");
        List<String> objectNames = new ArrayList<>();
        objectNames.add("redis.*");
        objectNames.add(".*test");

        ObjectMetricsCollectorTask objectMetricsCollectorTask = new ObjectMetricsCollectorTask("displayname", "localhost:8080", objectNames, stat, configuration, metricWriteHelper, phaser );
        objectMetricsCollectorTask.run();

        verify(metricWriteHelper, times(1)).transformAndPrintMetrics(pathCaptor.capture());
        List<com.appdynamics.extensions.metrics.Metric> objectMetricList = pathCaptor.getAllValues().get(0);
        Assert.assertTrue(objectMetricList.size() == 2);
        Assert.assertEquals("Custom Metrics|Redis Enterprise|displayname|database|test|Status", objectMetricList.get(0).getMetricPath());
        Assert.assertEquals("Custom Metrics|Redis Enterprise|displayname|database|redis-test|Status", objectMetricList.get(1).getMetricPath());
    }


}
