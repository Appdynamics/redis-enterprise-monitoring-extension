/*
 * Copyright 2018. AppDynamics LLC and its affiliates.
 * All Rights Reserved.
 * This is unpublished proprietary source code of AppDynamics LLC and its affiliates.
 * The copyright notice above does not evidence any actual or intended publication of such source code.
 *
 */
package com.appdynamics.extensions.redis_enterprise;

import com.appdynamics.extensions.ABaseMonitor;
import com.appdynamics.extensions.TasksExecutionServiceProvider;
import com.appdynamics.extensions.redis_enterprise.config.Stats;
import com.appdynamics.extensions.redis_enterprise.utils.Constants;
import com.appdynamics.extensions.util.AssertUtils;

import java.util.List;
import java.util.Map;

/**
 * @author: {Vishaka Sekar} on {7/11/19}
 */
public class RedisEnterpriseMonitor extends ABaseMonitor {

    @Override
    protected String getDefaultMetricPrefix () {
        return Constants.DEFAULT_METRIC_PREFIX;
    }

    @Override
    public String getMonitorName () {
        return Constants.REDIS_ENTERPRISE;
    }

    @Override
    protected void initializeMoreStuff (Map<String, String> args) {
        this.getContextConfiguration().setMetricXml(args.get("metrics-file"), Stats.class);
    }

    @Override
    protected void doRun (TasksExecutionServiceProvider tasksExecutionServiceProvider) {
        List<Map<String, ?>> servers =  getServers();
        for (Map<String, ?> server : servers) {
            RedisEnterpriseMonitorTask task = new RedisEnterpriseMonitorTask(tasksExecutionServiceProvider.getMetricWriteHelper(), this.getContextConfiguration(),server);
            AssertUtils.assertNotNull(server.get(Constants.DISPLAY_NAME), "The displayName can not be null");
            tasksExecutionServiceProvider.submit(server.get(Constants.DISPLAY_NAME).toString(), task);
        }
    }

    @Override
    protected List<Map<String, ?>> getServers () {
        return (List<Map<String, ?>>) getContextConfiguration().getConfigYml().get(Constants.SERVERS);
    }

}
