package com.maxent.dscache.cache.client;

import com.maxent.dscache.cache.CacheClusterViewer;
import com.maxent.dscache.cache.TestCacheEntry;
import com.maxent.dscache.cache.client.response.CacheSaveResponse;
import com.maxent.dscache.cache.client.response.CacheSearchResponse;
import com.maxent.dscache.common.tools.JsonUtils;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by alain on 16/9/16.
 */
public class CacheGroupClientTest {
    @Test
    public void search() throws Exception {
        CacheClusterViewer cacheClusterViewer = new CacheClusterViewer();
        CacheGroupClient cacheGroupClient = new CacheGroupClient(cacheClusterViewer);
        TestCacheEntry testCacheEntry = new TestCacheEntry();
        testCacheEntry.setField1("field1");
        testCacheEntry.setField2("field2");
        CacheSearchResponse response = cacheGroupClient.search("cache_group_test1", testCacheEntry);
        System.out.println(JsonUtils.toJson(response));
    }

    @Test
    public void save() throws Exception {
        CacheClusterViewer cacheClusterViewer = new CacheClusterViewer();
        CacheGroupClient cacheGroupClient = new CacheGroupClient(cacheClusterViewer);
        TestCacheEntry testCacheEntry = new TestCacheEntry();
        testCacheEntry.setField1("field1");
        testCacheEntry.setField2("field2");
        CacheSaveResponse response = cacheGroupClient.save("cache_group_test1", testCacheEntry);
        System.out.println(JsonUtils.toJson(response));
    }

    @Test
    public void create() throws Exception {

    }

    @Test
    public void delete() throws Exception {

    }

}