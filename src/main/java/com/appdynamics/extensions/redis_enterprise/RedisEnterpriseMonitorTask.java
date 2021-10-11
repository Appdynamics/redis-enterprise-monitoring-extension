package com.appdynamics.extensions.redis_enterprise;
import com.appdynamics.extensions.AMonitorTaskRunnable;
import com.appdynamics.extensions.MetricWriteHelper;
import com.appdynamics.extensions.conf.MonitorContextConfiguration;
import com.appdynamics.extensions.http.HttpClientUtils;
import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import com.appdynamics.extensions.redis_enterprise.config.Stat;
import com.appdynamics.extensions.redis_enterprise.config.Stats;
import com.appdynamics.extensions.redis_enterprise.metrics.ClusterMetricsCollectorTask;
import com.appdynamics.extensions.redis_enterprise.metrics.ObjectMetricsCollectorTask;
import com.appdynamics.extensions.redis_enterprise.utils.Constants;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author: {Vishaka Sekar} on {7/11/19}
 */

public class RedisEnterpriseMonitorTask implements AMonitorTaskRunnable {
    private static final Logger LOGGER = ExtensionsLoggerFactory.getLogger(RedisEnterpriseMonitorTask.class);
    private final MonitorContextConfiguration configuration;
    private final Map<String, ?> server;
    private final MetricWriteHelper metricWriteHelper;
    private Phaser phaser;

    RedisEnterpriseMonitorTask (MetricWriteHelper metricWriteHelper, MonitorContextConfiguration configuration, Map<String, ?> server) {
        this.configuration = configuration;
        this.server = server;
        this.metricWriteHelper = metricWriteHelper;
        this.phaser = new Phaser();
        phaser.register();
    }

    @Override
    public void run () {

        Map<String, ?> config = configuration.getConfigYml();
        AtomicInteger heartBeat = getConnectionStatus(server);
        metricWriteHelper.printMetric(configuration.getMetricPrefix() + "|" + server.get(Constants.DISPLAY_NAME).toString() + "|" + "Connection Status", String.valueOf(heartBeat.get()), "AVERAGE", "AVERAGE", "INDIVIDUAL");
        if (heartBeat.get() == 1) {
            Map<String, ?> objects = (Map<String, ?>) config.get("objects");
            String uri = server.get(Constants.URI).toString();
            String displayName = server.get(Constants.DISPLAY_NAME).toString();

            if (objects.size() > 0) {
                /* Any "object" in Redis Enterprise has to be of type database or node or shard.
                * For each objectType, collect the objectNames that need to be monitored, from config.yml.
                * objectNames can be regexes.
                 */
                for (Map.Entry<String, ?> object : objects.entrySet()) {
                    LOGGER.info("Starting metric collection for object [{}] on server [{}]", object.getKey(), displayName);
                    String objectType = object.getKey();
                    List<String> objectNames = (List<String>) object.getValue();
                    if (!objectNames.isEmpty()) {
                        collectObjectMetrics(displayName, uri, objectType, objectNames);
                    } else {
                        LOGGER.info("No object names of type [{}] found in config.yml", objectType);
                    }
                }
            }
            else{
                LOGGER.info("No object names found in config.yml, not collecting Db Metrics, Shard Metrics or Node Metrics");
            }
            //For each server, collect cluster level metrics
            collectClusterMetrics(displayName, uri);
        }
        LOGGER.info("Cannot connect to cluster {} ", server.get(Constants.DISPLAY_NAME).toString());
        phaser.arriveAndAwaitAdvance();
    }

    private AtomicInteger getConnectionStatus (Map<String, ?> server) {
        String url = getConnectionUrl(server);
        AtomicInteger heartbeat = new AtomicInteger(0);
        String response = HttpClientUtils.getResponseAsStr(this.configuration.getContext().getHttpClient(), url);
        if(response != null){
          heartbeat.set(1);
        }
        return heartbeat;
    }

    private String getConnectionUrl (Map<String, ?> server) {
        return server.get(Constants.URI).toString() + "/v1/";
    }

    /**
     * This method collects the stat data from metrics.xml for each objectType by matching on statType field.
     * Here, we co-relate metrics from metrics.xml to objectType.
     * Launches 3 tasks - one for DB, one for node, one for shard metrics.
     */
    private void collectObjectMetrics (String displayName, String uri, String objectType, List<String> names) {
        Stats stats = (Stats) configuration.getMetricsXml();
        Stat[] stat = stats.getStat();
        for (Stat statistic : stat) {
            if (objectType.equals(statistic.getType())) {
                ObjectMetricsCollectorTask objectMetricsCollectorTask = new ObjectMetricsCollectorTask(displayName, uri, names, statistic, configuration, metricWriteHelper, phaser);
                configuration.getContext().getExecutorService().execute(statistic.getType() + " task - " , objectMetricsCollectorTask);
            }
        }
    }

    private void collectClusterMetrics (String displayName, String uri){
        ClusterMetricsCollectorTask clusterMetricsCollectorTask = new ClusterMetricsCollectorTask(displayName, uri, configuration, metricWriteHelper, phaser);
        configuration.getContext().getExecutorService().execute("ClusterMetricsTask", clusterMetricsCollectorTask);
    }

    @Override
    public void onTaskComplete () {
        LOGGER.info("All tasks for host [{}] finished", server.get(Constants.DISPLAY_NAME));
    }
}
