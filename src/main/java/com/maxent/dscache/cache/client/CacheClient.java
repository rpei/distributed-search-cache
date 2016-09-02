package com.maxent.dscache.cache.client;

import com.maxent.dscache.api.rest.request.RestCacheSearchRequest;
import com.maxent.dscache.api.rest.response.RestCacheSearchResponse;
import com.maxent.dscache.cache.*;
import com.maxent.dscache.cache.client.response.CacheSearchResponse;
import com.maxent.dscache.common.http.HttpClient;
import com.maxent.dscache.common.tools.JsonUtils;

/**
 * Created by alain on 16/8/20.
 */
public class CacheClient {

    private CacheClusterService clusterCenter;

    public CacheClient(CacheClusterService clusterCenter) {
        this.clusterCenter = clusterCenter;
    }

    public CacheSearchResponse search(String cacheName, ICacheEntry entry) {

        String keys = entry.key();
        CacheMeta cache = clusterCenter.getCache(cacheName);
        int partition = cache.getPartitioner().getPartition(keys);
        int subCacheId = partition / cache.getPartitionsPerSubCache();

        SubCacheMeta subCacheMeta = cache.getSubCacheMetas().get(subCacheId);
        // TODO: choose replication
        Host host = subCacheMeta.getReplicationMetas().get(0).getHost();

        String url = String.format("http://%s:%d", host.getHost(), host.getPort());
        String path = "/subcache/search";
        HttpClient httpClient = new HttpClient();
        RestCacheSearchRequest restCacheSearchRequest = new RestCacheSearchRequest();
        restCacheSearchRequest.setCacheName(cacheName);
        restCacheSearchRequest.setEntries(entry);
        RestCacheSearchResponse restCacheSearchResponse =
                httpClient.post(url, path, new RestCacheSearchRequest(), RestCacheSearchResponse.class);
        return new CacheSearchResponse(
                restCacheSearchResponse.getScores(),
                restCacheSearchResponse.getEntries());
    }

    public static void main(String[] args) {
        CacheClusterService clusterService = new CacheClusterService();
        CacheClient cacheClient = new CacheClient(clusterService);
        TestCacheEntry testCacheEntry = new TestCacheEntry();
        testCacheEntry.setField1("field1");
        testCacheEntry.setField2("field2");
        CacheSearchResponse response = cacheClient.search("cache-test1", testCacheEntry);
        System.out.println(JsonUtils.toJson(response));
    }
}
