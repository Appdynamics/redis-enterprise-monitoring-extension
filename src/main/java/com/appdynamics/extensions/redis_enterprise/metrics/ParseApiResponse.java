package com.appdynamics.extensions.redis_enterprise.metrics;
import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.extensions.redis_enterprise.config.Stat;
import com.appdynamics.extensions.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;

/**
 * @author: Vishaka Sekar on 7/26/19
 */
class ParseApiResponse {

    private static final Logger LOGGER = ExtensionsLoggerFactory.getLogger(ParseApiResponse.class);
    private JsonNode metricsApiResponse;
    private String metricPrefix;
    private static ObjectMapper objectMapper = new ObjectMapper();
    private List<Metric> metricList = Lists.newArrayList();

     ParseApiResponse(JsonNode metricsApiResponse, String metricPrefix ){
        this.metricsApiResponse = metricsApiResponse;
        this.metricPrefix = metricPrefix;
    }

     List<Metric> extractMetricsFromApiResponse (Stat stat, JsonNode jsonNode) {
         String[] metricPathTokens;
         if (metricsApiResponse != null) {
             if(stat.getStats()!=null){
                 for(Stat childStat: stat.getStats()) {
                     extractMetricsFromApiResponse(childStat, JsonUtils.getNestedObject(jsonNode, childStat.getNameElement()));
                 }
             }
             for (com.appdynamics.extensions.redis_enterprise.config.Metric metricFromConfig : stat.getMetric()) {

                 String value = JsonUtils.getTextValue(jsonNode, metricFromConfig.getAttr());
                 if (value != null) {
                     LOGGER.debug("Processing metric [{}] ", metricFromConfig.getAttr());
                     metricPathTokens = metricFromConfig.getAttr().split("\\|");
                     Map<String, String> propertiesMap = objectMapper.convertValue(metricFromConfig, Map.class);
                     Metric metric = new Metric(metricFromConfig.getAttr(), value, propertiesMap, metricPrefix, metricPathTokens);
                     metricList.add(metric);
                 } else {
                     LOGGER.debug("Metric [{}] not found in response", metricFromConfig.getAttr());
                 }
             }
             return metricList;
         }
         else{
             LOGGER.info("No metrics received from Redis Enterprise");
             return null;
         }
     }
}
