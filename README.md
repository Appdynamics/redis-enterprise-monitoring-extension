## AppDynamics Monitoring Extension for Redis Enterprise
------------------------------------------------------------

## Use Case
[Redis Enterprise Software](https://docs.redislabs.com/latest/rs/) is from Redis Labs which enhances the open-source Redis with multiple deployment choices (public and private clouds, on-premises, hybrid, RAM-Flash combination), topology and support for very large data-set sizes. The AppDynamics Monitoring Extension for Redis Enterprise will collect metrics from Redis Enterprise clusters and send them to AppDynamics Controller.

## Prerequisites

1. Before the extension is installed, the prerequisites mentioned [here](https://community.appdynamics.com/t5/Knowledge-Base/Extensions-Prerequisites-Guide/ta-p/35213) need to be met. Please do not proceed with the extension installation if the specified prerequisites are not met.
2. Download and install [Apache Maven](https://maven.apache.org/) which is configured with `Java 8` to build the extension artifact from source. You can check the java version used in maven using command `mvn -v` or `mvn --version`. If your maven is using some other java version then please download java 8 for your platform and set JAVA_HOME parameter before starting maven.
3. The extension needs to be able to connect to Redis Enterprise in order to collect and send metrics. To do this, you will have to either establish a remote connection in between the extension and the product, or have an agent on the same machine running the product in order for the extension to collect and send the metrics.

## Installation
1. Clone the "redis-enterprise-monitoring-extension" repo using `git clone <repoUrl>` command.
2. Run "mvn clean install" from redis-enterprise-monitoring-extension
3. Unzip the RedisEnterpriseMonitor-VERSION.zip from targets folder to the "<MachineAgent_Dir>/monitors" directory
4. Configure the extension by referring to the section listed below. The metricPrefix of the extension has to be configured as 
   specified [here](https://community.appdynamics.com/t5/Knowledge-Base/How-do-I-troubleshoot-missing-custom-metrics-or-extensions/ta-p/28695#Configuring%20an%20Extension). Please make sure that the right metricPrefix is chosen based on your machine agent deployment, otherwise this could lead to metrics not being visible in the controller.
5. Restart the machine agent.

Please place the extension in the "monitors" directory of your Machine Agent installation directory. Do not place the extension in the "extensions" directory of your Machine Agent installation directory.

## Configuration 
In order to use this extension, the following files need to be configured - config.yml and metrics.xml. Here's how to configure those files. 

### Config.yml
* Configure the RedisEnterprise monitoring extension by editing the config.yml file in `<MACHINE_AGENT_HOME>/monitors/RedisEnterprise/`
* Enter the cluster endpoint you are monitoring in the `servers` section of the config.yml. This can be configured to collect metrics from the cluster along with all nodes, databases, and shards in the cluster.
* Configure the RedisEnterprise instances by specifying the URI(required), username(required), password(required) of the RedisEnterprise account, 
encryptedPassword(only if password encryption required), proxy(optional), useSSL(set to true if SSL is required). If SSL is required, please configure the `connection` section.
```
servers:
  - uri: "http://localhost:9443"
    username: "admin" # user should have privileges in RedisEnterprise
    password: "admin"
    encryptedPassword: ""
    useSSL: "false"
    displayName: "cluster1"
```
* If you wish to monitor multiple clusters from one extension, please pick one node from each cluster and add them in the servers section. 
```
servers:
  - uri: "http://redis.enterprise.one:5984"
    username: "admin" # user should have privileges in RedisEnterprise
    password: "admin"
    encryptedPassword: ""
    useSSL: "false"
    displayName: "cluster1"   # cluster 1 endpoint

  - uri: "http://redis.enterprise.two:5984"
    username: "admin" # user should have privileges in RedisEnterprise
    password: "admin"
    encryptedPassword: ""
    useSSL: "false"
    displayName: "cluster2"   # cluster 2 endpoint

<<To add more clusters, simply add  each cluster here>>
```
 
* Any changes to the config.yml does <b>not</b> require the machine agent to be restarted. 
* Please copy all the contents of the config.yml file and go [here](https://jsonformatter.org/yaml-validator) . On reaching the website, paste the contents and press the “Validate YAML” button.
If you get a valid output, that means your formatting is correct and you may move on to the next step.

### Metrics.xml
* The metrics.xml is a configurable file with the list of all metrics that the extension will fetch. 
* The metrics.xml is pre-configured with all from [cluster/stats endpoint](https://storage.googleapis.com/rlecrestapi/rest-html/http_rest_api.html#get--v1-cluster-stats-last), [bdbs/stats endpoint](https://storage.googleapis.com/rlecrestapi/rest-html/http_rest_api.html#get--v1-bdbs-stats-last), [shards/stats endpoint](https://storage.googleapis.com/rlecrestapi/rest-html/http_rest_api.html#get--v1-shards-stats-last) and [nodes/stat endpoint](https://storage.googleapis.com/rlecrestapi/rest-html/http_rest_api.html#get--v1-nodes-stats-last).
* Please un-comment metrics that are needed from metrics.xml.
* The metrics.xml can be configured to report only those metrics that are required. Please remove or comment out metrics that you don't require. 
* For configuring the metrics, the following properties can be used:

| Metric Property   |   Default value |         Possible values         |                                              Description                                                       |
| :---------------- | :-------------- | :------------------------------ | :------------------------------------------------------------------------------------------------------------- |
| alias             | metric name     | Any string                      | The substitute name to be used in the metric browser instead of metric name.                                   |
| aggregationType   | "AVERAGE"       | "AVERAGE", "SUM", "OBSERVATION" | [Aggregation qualifier](https://docs.appdynamics.com/display/latest/Build+a+Monitoring+Extension+Using+Java)    |
| timeRollUpType    | "AVERAGE"       | "AVERAGE", "SUM", "CURRENT"     | [Time roll-up qualifier](https://docs.appdynamics.com/display/latest/Build+a+Monitoring+Extension+Using+Java)   |
| clusterRollUpType | "INDIVIDUAL"    | "INDIVIDUAL", "COLLECTIVE"      | [Cluster roll-up qualifier](https://docs.appdynamics.com/display/latest/Build+a+Monitoring+Extension+Using+Java)|
| multiplier        | 1               | Any number                      | Value with which the metric needs to be multiplied.                                                            |
| convert           | null            | Any key value map               | Set of key value pairs that indicates the value to which the metrics need to be transformed. eg: UP:0, DOWN:1  |
| delta             | false           | true, false                     | If enabled, gives the delta values of metrics instead of actual values.   |

## Metrics
* The extension reports metrics from all 'Active' clusters, nodes, object and shards.

The following metrics are reported under each `cluster` name:

```
Free Memory 
Available Memory 
Provisional Memory 
Available Flash 
Provisional Flash 
Cpu Time - User 
Cpu Time - System 
Cpu Idle Time 
Available Ephemeral Disk Space 
Available Persistent Storage 
Ephemeral Storage - Free 
Persistent Storage - Free 
Request Rate 
Average Node Latency 
Number of Client Connections 
Ingress Rate 
Egress Rate 
BigRedis Key Access Per Sec 
BigRedis IO ops 
BigRedis Throughput 
BigRedis Memory - Free 
```

The following metrics are reported under `database` section:
```
Number of Keys
Evicted Objects 
Expired Objects 
Instantaneous Ops/sec 
Read Hits 
Read Misses 
Write Hits 
Write Misses 
PubSub Channels 
PubSub Patterns 
RAM Frag Ratio 
Disk Frag Ratio 
Bigstore KV Ops 
Bigstore IO ops 
Bigstore throughput 
BigRedis RAM Hits 
BigRedis Flash Hits 
BigRedis RAM Writes 
BigRedis Flash Writes 
BigRedis Deletes - RAM 
BigRedis Deletes - Flash 
BigRedis IO ops 
BigRedis Flash IO ops 
BigRedis Flash IO Reads 
BigRedis Flash IO Writes 
BigRedis Flash IO Deletes 
BigRedis Flash IO Read Throughput 
BigRedis Flash IO Write Throughput 
BigRedis RAM Overhead 
BigRedis RAM Value Count 
BigRedis Flash Value Count 
BigRedis Used Memory 
Redis Lua Scripting Heap Size 
BigRedis RAM Used 
BigRedis Flash Used 
Client Connection Count 
New Connections Per Sec 
Ingress Bytes 
Egress Bytes 
DB Request Rate - Read 
DB Response Rate - Read 
DB Request Rate - Write 
DB Response Rate - Write 
DB Request Rate - Non Read/Write 
DB Response Rate - Non Read/Write  
Total Request Rate 
Total Response Rate 
Number of Monitor Sessions 
Average Read latency 
Average Write Latency 
Average Latency - Other 
Average Latency 
BigRedis Flash Used  
BigRedis Flash Used  
BigRedis Flash Used  
BigRedis Flash Used  
BigRedis Flash Used  
BigRedis Flash Used  
BigRedis Flash Used  
```

The following metrics are reported under `node` section:

```
Free Memory 
Available Memory 
Provisional Memory 
Available Flash
Provisional Flash 
Cpu Time - User  
Cpu Time - System 
Cpu Idle Time 
Available Ephemeral Disk Space 
Available Persistent Storage 
Ephemeral Storage - Free 
Persistent Storage - Free 
Request Rate 
Average Node Latency 
Number of Client Connections 
Ingress Rate 
Egress Rate 
BigRedis Read/Write ops 
BigRedis IO ops 
BigRedis Throughput 
BigRedis Memory - Free 
aof rewrites 
```
The following metrics are reported under `shard` section:

```
AOF Rewrites In Progress  
Avg TTL 
BigStore IO 
BigStore Throughput 
BigStore Key Access Per Sec  
BigStore Key Count - Flash 
BigStore Key Count - RAM 
BigRedis Fetch - RAM 
BigRedis Fetch - Flash 
BigRedis Writes - RAM 
BigRedis Writes - Flash 
available_flash 
BigRedis Deletes - Flash 
BigRedis IO Ratio - RAM 
BigRedis IO Ratio - Flash 
BigStore IO Reads 
BigStore IO Writes 
BigStore IO Deletes 
BigStore Read Throughput 
BIgStore Write Throughput 
Clients Blocked 
Clients Connected 
Evicted Objects 
Expired Objects 
Last RDB Save Time 
Memory Used 
Redis Lua Heap Size 
Peak Memory Used 
RSS 
Number of Keys 
PubSub Channels 
PubSub Patterns 
RDB Changes 
Read Hits 
Read Misses 
Total Requests 
Write Hits 
Write Misses 
Memory Frag Ratio 
Disk Frag Ratio 
Number of Expires 
Percent cores - User mode 
Percent cores - System mode 
Main Thread - User mode 
Main Thread - System mode 
Fork Process - User mode 
Fork Process - System mode
```

## Filtering the metrics
* The extension supports the filtering of Redis Enterprise metrics based on patterns of the name of each node/shard/db in a cluster. 
  The `objects` section in config.yml will help you filter out metrics from specific nodes/shard/db in your cluster(s).
   
```
  objects:
      database: ['test1','test2', 'db.*']
      node: ['172.*']
      shard: ['3']
```
 
* It supports wild card matching. All of database, node and shard support wildcards. Here are some examples with wildcard filtering on databases :
  
```
  ### This matches all names
   - database: ['.*']
  
  ### This matches all database names that start with dev and database names that start with test
   - database: ["dev.*", "test.*" ] 
  
  ### This matches nothing, no metrics will be fetched
   - nodes: [] 
  
  ### This matches nothing, no metrics will be fetched
   - nodes: [""] 
  ```

## Credentials Encryption
Please visit [this](https://community.appdynamics.com/t5/Knowledge-Base/How-to-use-Password-Encryption-with-Extensions/ta-p/29397) page to get detailed instructions on password encryption. The steps in this document will guide you through the whole process.

## Extensions Workbench
Workbench is an in-built feature provided with each extension in order to assist you to fine tune the extension setup before you actually deploy it on the controller. Please review this [document](https://community.appdynamics.com/t5/Knowledge-Base/How-to-use-the-Extensions-WorkBench/ta-p/30130) for how to use the Extensions WorkBench.

## Troubleshooting
##### Connectivity to Redis Enterprise Cluster
* In order for the extension to collect metrics successfully, the machine agent should be able to reach the RedisEnterprise clusters. 
  Please execute this command from the host on which the Machine Agent is running. 

```
    curl -u "youremail@example.com:yourRedisEnterprisePassword" https://your.redis.cluster.endpoint/v1/cluster
```
If your cluster is set-up over SSL, please use the `--cacert` option to specify your keys. 
Usually the endpoint is of the format: 

```
    https://localhost:9443/v1/cluster # port 9443 is the default SSL port to fetch metrics from Redis Enterprise cluster
or  http://localhost:8080/v1/cluster  # port 8080 is the default non-SSL port to fetch metrics from Redis Enterprise cluster

```
If the curl command gives a `200 OK` response, your cluster is reachable from Machine Agent. If not, please ensure connectivity from your machine agent host every Redis Enterprise cluster.

##### Displaying metrics on the AppDynamics Metric Browser
* Please follow the steps listed in the [troubleshooting document](https://community.appdynamics.com/t5/Knowledge-Base/How-to-troubleshoot-missing-custom-metrics-or-extensions-metrics/ta-p/28695) in order to troubleshoot your issue. These are a set of common issues that customers might have faced during the installation of the extension.

## Contributing
Always feel free to fork and contribute any changes directly via [GitHub](https://github.com/Appdynamics/redis-enterprise-monitoring-extension).

## Version
| Name                        |  Version                    | 
| :---------------------------| :---------------------------|
| Extension Version:          | 1.0.2                  |
| Tested On:                  | Redis Enterprise Software v5.4.x       |
| Operating System Tested On: | Mac OS, Linux               |
| Last updated On:            | March 12, 2021          |
| Change List | [ChangeLog](https://github.com/Appdynamics/redis-enterprise-monitoring-extension/blob/master/CHANGELOG.md)

**Note**: While extensions are maintained and supported by customers under the open-source licensing model, they interact with agents and Controllers that are subject to [AppDynamics’ maintenance and support policy](https://docs.appdynamics.com/latest/en/product-and-release-announcements/maintenance-support-for-software-versions). Some extensions have been tested with AppDynamics 4.5.13+ artifacts, but you are strongly recommended against using versions that are no longer supported.
