package com.appdynamics.extensions.redis_enterprise.metrics;

import com.appdynamics.extensions.ABaseMonitor;
import com.appdynamics.extensions.MetricWriteHelper;
import com.appdynamics.extensions.conf.MonitorContext;
import com.appdynamics.extensions.conf.MonitorContextConfiguration;
import com.appdynamics.extensions.http.HttpClientUtils;
import com.appdynamics.extensions.metrics.MetricCharSequenceReplacer;
import com.appdynamics.extensions.redis_enterprise.config.Metric;
import com.appdynamics.extensions.redis_enterprise.config.Stat;
import com.appdynamics.extensions.redis_enterprise.config.Stats;
import com.appdynamics.extensions.util.JsonUtils;
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
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Phaser;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * @author: {Vishaka Sekar} on {7/22/19}
 */
@RunWith(PowerMockRunner.class)
@PowerMockIgnore("javax.net.ssl.*")
@PrepareForTest({HttpClientUtils.class})

public class ObjectMetricsCollectorSubTaskTest {

    private MonitorContextConfiguration configuration;
    private MetricWriteHelper metricWriteHelper;
    private Phaser phaser  = new Phaser();
    private String metricPrefix =  "Custom Metrics|Redis Enterprise";
    ArgumentCaptor<List> pathCaptor = ArgumentCaptor.forClass(List.class);

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
        phaser.register();
    }

    @Test
    public void testMetricExtractionFromApiResponseForObjects() throws IOException {
        String displayName = "myCluster";
        String uid = "3";
        String objectStatsEndpoint = "https://localhost:9443/v1/bdbs/stats/last/3";
        String objectName = "test";

        PowerMockito.mockStatic(HttpClientUtils.class);
        ObjectMapper mapper = new ObjectMapper();
        File objectStatsResponse = new File("src/test/resources/bdbs-stats.json");
        JsonNode objectStatsJson = mapper.readValue(objectStatsResponse, JsonNode.class);

        when(HttpClientUtils.getResponseAsJson(any(CloseableHttpClient.class), anyString(), any(Class.class))).thenAnswer(
                new Answer() {
                    public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                        String url = (String) invocationOnMock.getArguments()[1];
                        File file = null;
                        if(url.contains("cluster/stats")){
                             file = new File("src/test/resources/cluster-stats.json");
                        }
                        JsonNode objectNode = mapper.readValue(file, JsonNode.class);
                        return objectNode;
                    }
                });
        com.appdynamics.extensions.redis_enterprise.config.Metric[] metrics = new Metric[1];
        com.appdynamics.extensions.redis_enterprise.config.Metric metric = new com.appdynamics.extensions.redis_enterprise.config.Metric();
        metric.setAttr("conns");
        metric.setAlias("conns");
        metrics[0] = metric;
        Stat stat = new Stat();
        stat.setMetric(metrics);

        Stat[] childStatArray = new Stat[1];

        Stat childStat = new Stat();
        childStat.setNameElement("conns1");
        com.appdynamics.extensions.redis_enterprise.config.Metric[] childMetrics = new Metric[1];
        com.appdynamics.extensions.redis_enterprise.config.Metric childMetric = new com.appdynamics.extensions.redis_enterprise.config.Metric();
        childMetric.setAttr("metric1");
        childMetric.setAlias("metric1");
        childMetrics[0] = childMetric;
        childStatArray[0] = childStat;
        childStat.setMetric(childMetrics);

        Stat grandchildStat = new Stat();
        Stat[] grandchildStatArray = new Stat[1];
        grandchildStat.setNameElement("conns2");
        com.appdynamics.extensions.redis_enterprise.config.Metric[] childMetrics2 = new Metric[1];
        com.appdynamics.extensions.redis_enterprise.config.Metric childMetric2 = new com.appdynamics.extensions.redis_enterprise.config.Metric();
        childMetric2.setAttr("metric2");
        childMetric2.setAlias("metric2");
        childMetrics2[0] = childMetric2;
        grandchildStatArray[0] = grandchildStat;
        grandchildStat.setMetric(childMetrics2);

        childStat.setStats(grandchildStatArray);
        stat.setStats(childStatArray);

        //cluster stat
        Stats stats = new Stats();
        Stat[] superStatArray = new Stat[1];
        Stat clusterStat = new Stat();
        clusterStat.setType("cluster");
        clusterStat.setMetric(metrics);
        superStatArray[0] = clusterStat;
        stats.setStat(superStatArray);
        when(configuration.getMetricsXml()).thenReturn(stats);

        ObjectMetricsCollectorSubTask objectMetricsCollectorSubTask = new ObjectMetricsCollectorSubTask(displayName, objectStatsEndpoint, uid, objectName, configuration, metricWriteHelper, stat, JsonUtils.getNestedObject(objectStatsJson, uid), phaser);
        objectMetricsCollectorSubTask.run();

        ClusterMetricsCollectorTask clusterMetricsCollectorTask = new ClusterMetricsCollectorTask("myCluster", "https://localhost:9443/cluster/stats/last",configuration,metricWriteHelper,phaser);
        clusterMetricsCollectorTask.run();

        verify(metricWriteHelper, times(2)).transformAndPrintMetrics(pathCaptor.capture());
        List<com.appdynamics.extensions.metrics.Metric> objectMetricList = pathCaptor.getAllValues().get(0);

        Assert.assertTrue(objectMetricList.get(0).getMetricName().equals(childMetric2.getAlias()));
        Assert.assertTrue(objectMetricList.get(1).getMetricName().equals(childMetric.getAlias()));
        Assert.assertTrue(objectMetricList.get(2).getMetricName().equals(metric.getAlias()));

        List<com.appdynamics.extensions.metrics.Metric> clusterMetricList = pathCaptor.getAllValues().get(1);
        Assert.assertTrue(clusterMetricList.get(0).getMetricName().equals(metric.getAlias()));
    }

}
