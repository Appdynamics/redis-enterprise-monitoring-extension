package com.appdynamics.extensions.redis_enterprise.metrics;

import com.appdynamics.extensions.MetricWriteHelper;
import com.appdynamics.extensions.conf.MonitorContextConfiguration;
import com.appdynamics.extensions.http.HttpClientUtils;
import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.extensions.redis_enterprise.config.Stat;
import com.appdynamics.extensions.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Phaser;

/**
 * @author: Vishaka Sekar on 7/22/19
 */
public class ObjectMetricsCollectorTask implements  Runnable {
    private static final Logger LOGGER = ExtensionsLoggerFactory.getLogger(ObjectMetricsCollectorTask.class);
    private MonitorContextConfiguration configuration;
    private Stat stat;
    private List<String> objectNames;
    private String displayName;
    private String uri;
    private MetricWriteHelper metricWriteHelper;
    private Phaser phaser;
    private Map<String, String> objectNameToStatus = Maps.newHashMap();

    public ObjectMetricsCollectorTask (String displayName, String uri, List<String> objectNames, Stat stat, MonitorContextConfiguration configuration, MetricWriteHelper metricWriteHelper, Phaser phaser){
        this.configuration = configuration;
        this.objectNames = objectNames;
        this.stat = stat;
        this.displayName = displayName;
        this.uri = uri;
        this.metricWriteHelper = metricWriteHelper;
        this.phaser = phaser;
        phaser.register();
    }

    @Override
    public void run () {
        try {
            collectMetrics(displayName, uri, objectNames, stat);
        }catch(Exception e ){
            LOGGER.info("Exception while collecting metrics for server {}", displayName , e);
        }finally {
            phaser.arriveAndDeregister();
        }
    }

    /**
     * For each "server"  @param displayName in config.yml, build  the endpoint url to get all stats.
     * Passes all objectName patterns(for the current objectType) from "objects" field in config.yml
     */
    private void collectMetrics (String displayName, String uri, List<String> objectNames, Stat stat) {
        if(stat.getStatsUrl() != null) {
            String statsUrl = uri + stat.getStatsUrl();
            if (!objectNames.isEmpty()) {
                    collectRedisObjectDetails(displayName, uri, objectNames, stat, statsUrl);
            }
        }
        else {
            LOGGER.debug("statsUrl is null. Please provide statistic url for [{}]", stat.getNameElement());
        }
    }

    /**
     * Call the /v1/{objectType} endpoint.
     * This call will give us the id for each objectName. Id is needed for collecting the stats.
     */
    private void collectRedisObjectDetails(String displayName, String uri, List<String> objectNames, Stat stat, String statsUrl) {
        ArrayNode objectDetailsJson;

        if(!Strings.isNullOrEmpty(stat.getUrl()) && !Strings.isNullOrEmpty(stat.getIdElement())) {
            String url = uri + stat.getUrl();
            objectDetailsJson = HttpClientUtils.getResponseAsJson(this.configuration.getContext().getHttpClient(), url, ArrayNode.class);

            if (objectDetailsJson != null && objectDetailsJson.size() > 0) {
                collectObjectStats(displayName, objectNames, stat, statsUrl, objectDetailsJson);
            }
            else {
                LOGGER.info("Did not find ID for the objectNames [{}]", objectNames);
            }
        }
    }

    /**
     * Collects the stats for all objects of the current objectType in one API call. The response is in the format <ID, statsJson>
     * This is done to reduce # API calls. With map of <ids, objectNamesThatMatchRegex>, launch a task for each id.
     */
    private void collectObjectStats(String displayName, List<String> objectNames, Stat stat, String statsUrl, ArrayNode objectDetailsJson) {
        JsonNode objectsStatsJson;
        objectsStatsJson = HttpClientUtils.getResponseAsJson(this.configuration.getContext().getHttpClient(), statsUrl,  JsonNode.class);
        Map<String, String> idToNameMap = getIdToNameMap(objectDetailsJson, objectNames, stat.getIdElement(), stat.getNameElement(), stat.getType());

        //print status metric for all discovered objects
        printStatusMetric(objectNameToStatus);

        //collect and print other metrics
        for (Map.Entry<String, String> idNamePair : idToNameMap.entrySet()) {
            String objectId =  idNamePair.getKey();
            String objectName =  idNamePair.getValue();
            LOGGER.debug("Starting metric collection for object [{}] [{}] with id [{}] in [{}]", stat.getType(), objectName, objectId, displayName);
            JsonNode objectStats = JsonUtils.getNestedObject(objectsStatsJson, objectId);
            ObjectMetricsCollectorSubTask task = new ObjectMetricsCollectorSubTask(displayName, statsUrl, objectId, objectName,
                    configuration, metricWriteHelper, stat, objectStats, phaser);
            configuration.getContext().getExecutorService().execute(stat.getType() + " task - " + idNamePair.getValue(), task);
        }
    }

    /**
     * Maintains a map of <id,objectName> across all regex patterns.
     */
    private Map<String, String> getIdToNameMap(ArrayNode jsonNodes,
                                               List<String> objectNamePatterns,
                                               String idElement,
                                               String statName,
                                               String statType) {
        Map<String, String> globalMapOfIdsToNames = new HashMap<>();

        for (String objectNamePattern : objectNamePatterns) {
            Map<String, String> objectIdToNames = getIdToNameMapPerPattern(statName, statType, objectNamePattern, idElement, jsonNodes);
            if (!objectIdToNames.isEmpty()) {
                globalMapOfIdsToNames.putAll(objectIdToNames);
            }
            else {
                LOGGER.debug("[{}] not found in Redis Enterprise server {}", objectNamePattern, displayName);
            }
        }
        return globalMapOfIdsToNames;
    }

    /**
     * For each @param statType ( statType = objectType = db/node/shard) - the id Field is named differently(it can be id, uid, addr).
     * The name of the id field is captured in the @param idElement field in metrics.xml.
     * This method constructs a map of <id, objectName> for each name regex.
     */
    private Map<String, String> getIdToNameMapPerPattern (String statName, String statType,
                                                         String objectNamePattern,
                                                         String idElement,
                                                         ArrayNode jsonNodes) {
       Map<String, String> idToName = new HashMap<>();

        for (JsonNode jsonNode : jsonNodes) {
            String objectName = extractValueFromJson(statName, jsonNode);
            if(objectName.matches(objectNamePattern)){
                LOGGER.debug("Wildcard match for {}", objectName);
                if( isActive(objectName, jsonNode, statType)){
                    if(jsonNode.get(idElement) != null) {
                        String id = jsonNode.get(idElement).isTextual() ?
                                jsonNode.get(idElement).textValue() : jsonNode.get(idElement).toString();
                        idToName.put(id, objectName);
                    }
                    else{
                        LOGGER.debug("The field called [{}] for [{}] is not found", idElement, objectName);
                    }
                }
                else{
                    LOGGER.debug("Object [{}] not active", objectName);
                }
            }
        }

        if(idToName.isEmpty()){
            LOGGER.info("The pattern [{}] did not match any active object in Redis Enterprise server {}", objectNamePattern, displayName);
        }
        return idToName;
    }

    /**
     * @param key to be searched in @param jsonNode
     *@return value of the key field
     */
    private String extractValueFromJson (String key, JsonNode jsonNode) {
        return jsonNode.get(key).textValue();
    }

    /**
     * Extract status metric from json response. Populates objectNameToStatus map for printStatusMetric().
     * @return true(active) false(inactive)
     */
    private boolean isActive (String object, JsonNode jsonNode, String statType){
        if(extractValueFromJson("status", jsonNode).equalsIgnoreCase("active")){
            LOGGER.debug("Object [{}] is in active state",object);
            objectNameToStatus.put(statType + "|"+ object, "1");
            return true;
        }
        else{
            LOGGER.debug("Object [{}] is in [{}] state, not collection metrics", object, extractValueFromJson("status",jsonNode) );
            objectNameToStatus.put(statType + "|"+ object, "0");
            return false;
        }
    }

    /**
     * Prints "Status" metric for all discovered objects. This is different from Connection status
     * "Status" indicates whether each object(whether db/node/shard) is up.
     * @param objectNameToStatus  - collected from isActive() method.
     * @apiNote returns value of 1 if, 0 if not active
     */
    private void printStatusMetric(Map<String, String> objectNameToStatus) {
        List<Metric> metricList = Lists.newArrayList();
        for(Map.Entry objectStatus : objectNameToStatus.entrySet()) {
            Metric metric = new Metric("Status", objectStatus.getValue().toString(), configuration.getMetricPrefix() + "|" +
                    displayName + "|" + objectStatus.getKey() + "|" + "Status",  "OBSERVATION", "CURRENT", "INDIVIDUAL");
            metricList.add(metric);
        }
        metricWriteHelper.transformAndPrintMetrics(metricList);
    }

}
