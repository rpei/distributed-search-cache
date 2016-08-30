package com.maxent.dscache.cache;

import com.google.common.base.Charsets;
import com.maxent.dscache.api.rest.request.RestCreateSubCacheRequest;
import com.maxent.dscache.api.rest.request.RestDeleteSubCacheRequest;
import com.maxent.dscache.api.rest.response.RestCreateSubCacheResponse;
import com.maxent.dscache.api.rest.response.RestDeleteSubCacheResponse;
import com.maxent.dscache.cache.exceptions.*;
import com.maxent.dscache.common.http.HttpClient;
import com.maxent.dscache.common.partitioner.HashPartitioner;
import com.maxent.dscache.common.tools.ClassUtils;
import com.maxent.dscache.common.tools.JsonUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.framework.recipes.locks.InterProcessReadWriteLock;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by alain on 16/8/20.
 */
public class CacheClusterService {
    private static final Logger logger = LoggerFactory.getLogger(CacheClusterService.class);

    private static final long DEFAULT_START_VERSION = 0;

    private String zookeeperConnectionUrl = "127.0.0.1:2181";

    private CuratorFramework zkClient;

    private final String CACHE_CLUSTER_PATH = "/cache_cluster";
    private final String CACHES_PATH = StringUtils.join(CACHE_CLUSTER_PATH, "/caches");
    private final String HOSTS_PATH = StringUtils.join(CACHE_CLUSTER_PATH, "/hosts");
    private final String HOST_PATH_PREFIX = "host_";

    private final String CACHE_CLUSTER_INITIAL_VERSION = "0";

    private CacheClusterMeta cacheCluster;

    public CacheClusterService() throws RuntimeException {
        try {
            RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);
            zkClient = CuratorFrameworkFactory.newClient(zookeeperConnectionUrl, retryPolicy);
            zkClient.start();

            initClusterIfNot();

            cacheCluster = getCacheClusterMeta();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void close() {
        zkClient.close();
    }

    /**
     * cache_cluster
     * ├── caches
     * │   ├── cache1
     * │   │   ├── subcache1
     * │   │   │   ├── replication1
     * │   │   │   └── replication2
     * │   │   └── subcache2
     * │   └── cache2
     * └── hosts
     *
     * @throws Exception
     */
    private CacheClusterMeta doGetCacheClusterMeta() throws Exception {
        CacheClusterMeta cacheClusterMeta = new CacheClusterMeta();

        CacheClusterZnode cacheClusterZnode = JsonUtils.fromJson(
                new String(zkClient.getData().forPath(CACHE_CLUSTER_PATH), Charsets.UTF_8),
                CacheClusterZnode.class);

        /**
         * restore cluster version
         */
        cacheClusterMeta.setVersion(cacheClusterZnode.getVersion());

        /**
         * restore hosts
         */
        List<Host> hosts = new ArrayList<>();
        List<String> hostsPath = zkClient.getChildren().forPath(HOSTS_PATH);
        for (String hostPath : hostsPath) {
            String fullHostPath = StringUtils.join(HOSTS_PATH, "/", hostPath);
            Host host = JsonUtils.fromJson(
                    new String(zkClient.getData().forPath(fullHostPath), Charsets.UTF_8),
                    Host.class);
            hosts.add(host.getId(), host);
        }
        cacheClusterMeta.setHosts(hosts);


        /**
         * restore caches
         */
        List<CacheMeta> caches = new ArrayList<>();
        List<String> cachesPath = zkClient.getChildren().forPath(CACHES_PATH);
        for (String cachePath : cachesPath) {
            String fullCachePath = StringUtils.join(CACHES_PATH, "/", cachePath);
            CacheZnode cacheZnode = JsonUtils.fromJson(
                    new String(zkClient.getData().forPath(fullCachePath), Charsets.UTF_8),
                    CacheZnode.class);

            List<SubCacheMeta> subCaches = new ArrayList<>();
            List<String> subCachesPath = zkClient.getChildren().forPath(fullCachePath);
            for (String subCachePath : subCachesPath) {
                String fullSubCachePath = StringUtils.join(fullCachePath, "/", subCachePath);
                SubCacheZnode subCacheZnode = JsonUtils.fromJson(
                        new String(zkClient.getData().forPath(fullSubCachePath), Charsets.UTF_8),
                        SubCacheZnode.class);
                SubCacheMeta subCacheMeta = new SubCacheMeta();
                subCacheMeta.setId(subCacheZnode.getId());
                List<ReplicationMeta> replications = new ArrayList<>();
                List<String> replicationsPath = zkClient.getChildren().forPath(subCachePath);
                for (String replicationPath : replicationsPath) {
                    String fullReplicationPath = StringUtils.join(subCachePath, "/", replicationPath);
                    ReplicationZnode replicationZnode = JsonUtils.fromJson(
                            new String(zkClient.getData().forPath(fullReplicationPath), Charsets.UTF_8),
                            ReplicationZnode.class);
                    ReplicationMeta replicationMeta = new ReplicationMeta();
                    replicationMeta.setId(replicationZnode.getId());
                    replicationMeta.setHost(hosts.get(replicationZnode.getHostId()));
                    replicationMeta.setZkNodeName(replicationPath);
                    replications.add(replicationMeta);
                }
                subCacheMeta.setReplicationMetas(replications);
                subCaches.add(subCacheMeta);
            }

            CacheMeta cacheMeta = new CacheMeta();
            cacheMeta.setVersion(cacheZnode.getVersion());
            cacheMeta.setName(cacheZnode.getName());
            cacheMeta.setPartitionsPerSubCache(cacheZnode.getPartitionsPerSubCache());
            cacheMeta.setEntryClassName(cacheZnode.getEntryClassName());
            cacheMeta.setEntryClass(ClassUtils.loadClass(cacheZnode.getEntryClassName(), ICacheEntry.class));
            cacheMeta.setSubCacheMetas(subCaches);
            int partitions = cacheZnode.getPartitionsPerSubCache() * subCachesPath.size();
            cacheMeta.setPartitioner(new HashPartitioner(partitions));

            caches.add(cacheMeta);
        }
        cacheClusterMeta.setCaches(caches);
        return cacheClusterMeta;
    }

    public CacheClusterMeta getCacheClusterMeta() throws Exception {
        InterProcessReadWriteLock lock = new InterProcessReadWriteLock(zkClient, CACHE_CLUSTER_PATH);
        lock.readLock().acquire();
        try {
            return doGetCacheClusterMeta();
        } finally {
            // don't worry, if zookeeper connection closed, the lock will release by zookeeper.
            try {
                lock.readLock().release();
            } catch (Exception e) {
                logger.error(String.format("failed to release lock on zknode[%s]", CACHE_CLUSTER_PATH), e);
            }
        }
    }


    public CacheMeta getCache(String name) {
        for (CacheMeta cache : cacheCluster.getCaches()) {
            if (cache.getName().equals(name)) {
                return cache;
            }
        }
        return null;
    }

    private void createSubCachesInCluster(CacheMeta cacheMeta) throws CacheCreateFailureException {
        HttpClient httpClient = new HttpClient();
        List<SubCacheMeta> subCaches = cacheMeta.getSubCacheMetas();

        try {
            for (SubCacheMeta subCache : subCaches) {
                ReplicationMeta meta = subCache.getReplicationMetas().get(0);
                Host host = meta.getHost();
                String url = String.format("http://%s:%d", host.getHost(), host.getPort());
                String path = "/subcache/create";
                RestCreateSubCacheRequest restCreateSubCacheRequest = new RestCreateSubCacheRequest();
                restCreateSubCacheRequest.setName(cacheMeta.getName());
                restCreateSubCacheRequest.setEntryClassName(cacheMeta.getEntryClassName());
                restCreateSubCacheRequest.setSubCacheId(String.valueOf(subCache.getId()));
                restCreateSubCacheRequest.setPartitionsPerSubCache(cacheMeta.getPartitionsPerSubCache());
                restCreateSubCacheRequest.setBlocksPerPartition(cacheMeta.getBlocksPerPartition());
                restCreateSubCacheRequest.setBlockCapacity(cacheMeta.getBlockCapacity());
                RestCreateSubCacheResponse createCacheResponse =
                        httpClient.post(url, path, restCreateSubCacheRequest, RestCreateSubCacheResponse.class);
                if (createCacheResponse == null) {
                    throw new CacheCreateFailureException(String.format(
                            "failed to create subcache[%s]", JsonUtils.toJson(subCache)));
                }
                if (createCacheResponse.getError() != null) {
                    throw new CacheCreateFailureException(String.format(
                            "failed to create subcache[%s], error[%s]",
                            JsonUtils.toJson(subCache), createCacheResponse.getError()));
                }
            }
        } catch (Exception e) {
            try {
                for (SubCacheMeta subCache : subCaches) {
                    ReplicationMeta meta = subCache.getReplicationMetas().get(0);
                    Host host = meta.getHost();
                    String url = String.format("http://%s:%d", host.getHost(), host.getPort());
                    String path = "/subcache/delete";
                    RestDeleteSubCacheRequest restDeleteSubCacheRequest = new RestDeleteSubCacheRequest();
                    restDeleteSubCacheRequest.setName(cacheMeta.getName());
                    restDeleteSubCacheRequest.setSubCacheId(String.valueOf(subCache.getId()));
                    httpClient.post(url, path, restDeleteSubCacheRequest, RestDeleteSubCacheResponse.class);
                }
            } catch (Exception e1) {
                logger.error(String.format(
                        "failed to clean cacheMeta[%s] after create failed", JsonUtils.toJson(cacheMeta)));
            }
            throw e;
        }
    }

    private void doCreateCache(CacheMeta cacheMeta) throws Exception {
        String name = cacheMeta.getName();
        String cacheZkPath = StringUtils.join(CACHES_PATH, "/", name);
        CacheZnode cacheZnode = new CacheZnode();
        cacheZnode.setName(cacheMeta.getName());
        cacheZnode.setVersion(cacheMeta.getVersion());
        cacheZnode.setEntryClassName(cacheMeta.getEntryClassName());
        cacheZnode.setPartitionsPerSubCache(cacheMeta.getPartitionsPerSubCache());
        cacheZnode.setBlockCapacity(cacheMeta.getBlockCapacity());
        cacheZnode.setBlocksPerPartition(cacheMeta.getBlocksPerPartition());
        zkClient.create().forPath(cacheZkPath);
        zkClient.setData().forPath(cacheZkPath, JsonUtils.toJson(cacheZnode).getBytes(Charsets.UTF_8));

        for (SubCacheMeta subCacheMeta : cacheMeta.getSubCacheMetas()) {
            SubCacheZnode subCacheZnode = new SubCacheZnode();
            subCacheZnode.setId(subCacheMeta.getId());
            String subCacheZkPath = StringUtils.join(cacheZkPath, "/", subCacheMeta.getZkNodeName());
            zkClient.create().forPath(subCacheZkPath);
            zkClient.setData().forPath(subCacheZkPath, JsonUtils.toJson(subCacheZnode).getBytes(Charsets.UTF_8));

            for (ReplicationMeta replicationMeta : subCacheMeta.getReplicationMetas()) {
                ReplicationZnode replicationZnode = new ReplicationZnode();
                replicationZnode.setId(replicationMeta.getId());
                replicationZnode.setHostId(replicationMeta.getHost().getId());
                String replicationZkPath = StringUtils.join(subCacheZkPath, "/", replicationMeta.getZkNodeName());
                zkClient.create().forPath(replicationZkPath);
                zkClient.setData().forPath(replicationZkPath,
                        JsonUtils.toJson(replicationZnode).getBytes(Charsets.UTF_8));
            }
        }
    }

    public void createCache(String name, String entryClassName,
                            int subCaches, int partitionsPerSubCache,
                            int blockCapacity, int blocksPerPartition)
            throws Exception {
        InterProcessMutex lock = new InterProcessMutex(zkClient, CACHE_CLUSTER_PATH);
        lock.acquire();
        try {
            CacheClusterMeta cacheClusterMeta = doGetCacheClusterMeta();
            List<Host> hosts = cacheClusterMeta.getHosts();
            List<CacheMeta> caches = cacheClusterMeta.getCaches();
            for (CacheMeta cache : caches) {
                if (cache.getName().equals(name)) {
                    throw new CacheExistException(String.format("cache[%s] already exist", name));
                }
            }

            CacheMeta cacheMeta = new CacheMeta();
            cacheMeta.setVersion(String.valueOf(DEFAULT_START_VERSION));
            cacheMeta.setName(name);
            cacheMeta.setEntryClassName(entryClassName);
            cacheMeta.setEntryClass(ClassUtils.loadClass(entryClassName, ICacheEntry.class));
            cacheMeta.setBlockCapacity(blockCapacity);
            cacheMeta.setBlocksPerPartition(blocksPerPartition);

            List<SubCacheMeta> subCacheMetas = new ArrayList<>(subCaches);
            for (int i = 0; i < subCaches; i++) {
                SubCacheMeta subCacheMeta = new SubCacheMeta();
                subCacheMeta.setId(i);
                subCacheMeta.setZkNodeName(String.format("subcache_%d", i));
                ReplicationMeta replicationMeta = new ReplicationMeta();
                replicationMeta.setId(i);
                replicationMeta.setHost(hosts.get(i % hosts.size()));
                replicationMeta.setZkNodeName(String.format("replication_%d", 0));
                List<ReplicationMeta> replicationMetas = new ArrayList<>();
                replicationMetas.add(replicationMeta);
                subCacheMeta.setReplicationMetas(replicationMetas);
                subCacheMetas.add(subCacheMeta);
            }
            cacheMeta.setSubCacheMetas(subCacheMetas);
            cacheMeta.setPartitionsPerSubCache(partitionsPerSubCache);


            // 先改变集群的状态
            createSubCachesInCluster(cacheMeta);

            // 再改变集群在zookeeper中的状态
            doCreateCache(cacheMeta);

        } finally {
            try {
                lock.release();
            } catch (Exception e) {
                logger.error(String.format("failed to release lock on zknode[%s]", CACHE_CLUSTER_PATH), e);
            }
        }
    }

    public void doAddHost(Host host) throws Exception {
        String hostPath = StringUtils.join(
                HOSTS_PATH, "/", String.format("%s%d", HOST_PATH_PREFIX, host.getId()));
        zkClient.create().forPath(hostPath);
        zkClient.setData().forPath(hostPath, JsonUtils.toJson(host).getBytes(Charsets.UTF_8));
    }

    public void addHosts(List<Host> newHosts) throws Exception {
        InterProcessMutex lock = new InterProcessMutex(zkClient, CACHE_CLUSTER_PATH);
        lock.acquire();
        try {
            CacheClusterMeta cacheClusterMeta = doGetCacheClusterMeta();
            List<Host> hosts = cacheClusterMeta.getHosts();
            for (Host newHost : newHosts) {
                if (hosts.contains(newHost)) {
                    throw new CacheHostExistException(String.format(
                            "host[%s] already exist", JsonUtils.toJson(newHost)));
                }
            }

            int newHostIdStart = hosts.size();
            for (int i = 0; i < newHosts.size(); i++) {
                Host newHost = newHosts.get(i);
                newHost.setId(newHostIdStart + i);
                doAddHost(newHost);
            }
        } finally {
            try {
                lock.release();
            } catch (Exception e) {
                logger.error(String.format("failed to release lock on zknode[%s]", CACHE_CLUSTER_PATH), e);
            }
        }
    }

    public void initClusterIfNot() throws CacheCheckFailureException, CacheInitializeFailureException {
        try {
            if (zkClient.checkExists().forPath(CACHE_CLUSTER_PATH) != null) {
                return;
            }
        } catch (Exception e) {
            throw new CacheCheckFailureException(
                    String.format("failed to checkExists for path[%s]", CACHE_CLUSTER_PATH), e);
        }

        try {
            try {
                zkClient.create().forPath(CACHE_CLUSTER_PATH);
                CacheClusterZnode cacheClusterZnode = new CacheClusterZnode();
                cacheClusterZnode.setVersion(CACHE_CLUSTER_INITIAL_VERSION);
                zkClient.setData().forPath(CACHE_CLUSTER_PATH,
                        JsonUtils.toJson(cacheClusterZnode).getBytes(Charsets.UTF_8));
            } catch (KeeperException.NodeExistsException e) {
                logger.warn(String.format("zookeeper node[%s] exist", CACHE_CLUSTER_PATH), e);
            }

            try {
                zkClient.create().forPath(CACHES_PATH);
            } catch (KeeperException.NodeExistsException e) {
                logger.warn(String.format("zookeeper node[%s] exist", CACHES_PATH), e);
            }

            try {
                zkClient.create().forPath(HOSTS_PATH);
            } catch (KeeperException.NodeExistsException e) {
                logger.warn(String.format("zookeeper node[%s] exist", HOSTS_PATH), e);
            }
        } catch (Exception e) {
            throw new CacheInitializeFailureException("failed to check cluster", e);
        }
    }
}
