package com.appdynamics.extensions.redis_enterprise.metrics;

import com.appdynamics.extensions.MetricWriteHelper;
import com.appdynamics.extensions.conf.MonitorContextConfiguration;
import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.extensions.redis_enterprise.config.Stat;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;

import java.util.List;
import java.util.concurrent.Phaser;

/**
 * @author: Vishaka Sekar} on 7/14/19
 */
public class ObjectMetricsCollectorSubTask implements Runnable {

    private static final Logger LOGGER = ExtensionsLoggerFactory.getLogger(ObjectMetricsCollectorSubTask.class);
    private final MonitorContextConfiguration monitorContextConfiguration;
    private final String uid;
    private final String objectName;
    private final String statsEndpointUrl;
    private final MetricWriteHelper metricWriteHelper;
    private final String serverName;
    private Stat parentStat;
    private Phaser phaser;
    private JsonNode jsonNode;

    public ObjectMetricsCollectorSubTask (String displayName,
                                          String statsEndpointUrl,
                                          String uid,
                                          String objectName,
                                          MonitorContextConfiguration monitorContextConfiguration,
                                          MetricWriteHelper metricWriteHelper,
                                          Stat parentStat,
                                          JsonNode jsonNode,
                                          Phaser phaser) {
        this.uid = uid;
        this.objectName = objectName;
        this.statsEndpointUrl = statsEndpointUrl;
        this.monitorContextConfiguration = monitorContextConfiguration;
        this.metricWriteHelper = metricWriteHelper;
        this.serverName = displayName;
        this.parentStat = parentStat;
        this.phaser = phaser;
        this.jsonNode = jsonNode;
        this.phaser.register();
    }

    @Override
    public void run () {
        try {
            collectMetrics(parentStat);
        }catch (Exception e){
            LOGGER.info("Exception while collecting object metrics {}", objectName);
        } finally {
            phaser.arriveAndDeregister();
        }
    }

    private void collectMetrics (Stat stat) {

        LOGGER.debug("Extracting metricsFromConfig for [{}] for objectName [{}] ", statsEndpointUrl, objectName);
        ParseApiResponse parser = new ParseApiResponse(jsonNode, monitorContextConfiguration.getMetricPrefix()
                + "|" + serverName + "|" + stat.getType() + "|" + objectName);
        List<Metric> metricsList = parser.extractMetricsFromApiResponse(stat, jsonNode);
        metricWriteHelper.transformAndPrintMetrics(metricsList);
    }
}

