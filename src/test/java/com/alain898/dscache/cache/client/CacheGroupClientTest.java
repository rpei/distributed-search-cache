package com.alain898.dscache.cache.client;

import com.alain898.dscache.DSCache;
import com.alain898.dscache.cache.CacheClusterViewer;
import com.alain898.dscache.cache.CacheClusterViewerFactory;
import com.alain898.dscache.cache.TestCacheEntry;
import com.alain898.dscache.cache.client.response.*;
import com.alain898.dscache.common.tools.JsonUtils;
import com.typesafe.config.ConfigFactory;
import junit.framework.TestCase;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

/**
 * Created by alain on 16/9/16.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class CacheGroupClientTest {
    private static DSCache dsCache = null;

    @BeforeClass
    public static void setUp() throws Exception {
        dsCache = new DSCache();
        dsCache.start();
        CacheClusterViewerFactory.configure(ConfigFactory.load());
    }

    @AfterClass
    public static void tearDown() throws Exception {
        dsCache.stop();
        dsCache = null;
    }

    @Test
    public void test001_create() throws Exception {
        CacheClusterViewer cacheClusterViewer = CacheClusterViewerFactory.getCacheClusterViewer();
        CacheGroupClient cacheGroupClient = new CacheGroupClient(cacheClusterViewer);
        String cacheGroupName = "cache_group_test1";
        String entryClassName = "com.alain898.dscache.cache.TestCacheEntry";
        int cacheGroupCapacity = 256;
        int cachesNumber = 4;
        int subCachesPerCache = 2;
        int partitionsPerSubCache = 16;
        int blockCapacity = 100;
        int blocksPerPartition = 10;
        CreateCacheGroupResponse response = cacheGroupClient.create(
                cacheGroupName, entryClassName, cacheGroupCapacity,
                cachesNumber, subCachesPerCache, partitionsPerSubCache,
                blockCapacity, blocksPerPartition);
        String result = JsonUtils.toJson(response);
        System.out.println(result);
        TestCase.assertEquals("{\"message\":\"success\"}", result);
        Thread.sleep(2000);
    }

    @Test
    public void test002_save() throws Exception {
        CacheClusterViewer cacheClusterViewer = CacheClusterViewerFactory.getCacheClusterViewer();
        CacheGroupClient cacheGroupClient = new CacheGroupClient(cacheClusterViewer);
        TestCacheEntry testCacheEntry = new TestCacheEntry();
        testCacheEntry.setField1("field1");
        testCacheEntry.setField2("field2");
        CacheSaveResponse response = cacheGroupClient.save("cache_group_test1", testCacheEntry);
        String result = JsonUtils.toJson(response);
        System.out.println(result);
        TestCase.assertEquals("{\"message\":\"success\"}", result);
        Thread.sleep(2000);
    }

    @Test
    public void test003_search() throws Exception {
        CacheClusterViewer cacheClusterViewer = CacheClusterViewerFactory.getCacheClusterViewer();
        CacheGroupClient cacheGroupClient = new CacheGroupClient(cacheClusterViewer);
        TestCacheEntry testCacheEntry = new TestCacheEntry();
        testCacheEntry.setField1("field1");
        testCacheEntry.setField2("field2");
        CacheSearchResponse response = cacheGroupClient.search("cache_group_test1", testCacheEntry);
        String result = JsonUtils.toJson(response);
        System.out.println(result);
        TestCase.assertEquals("{\"scores\":[1.0],\"entries\":[{\"field1\":\"field1\",\"field2\":\"field2\"}]}", result);
        Thread.sleep(2000);
    }

    @Test
    public void test004_update() throws Exception {
        CacheClusterViewer cacheClusterViewer = CacheClusterViewerFactory.getCacheClusterViewer();
        CacheGroupClient cacheGroupClient = new CacheGroupClient(cacheClusterViewer);
        CacheGroupUpdateResponse response = cacheGroupClient.update("cache_group_test1", 4);
        String result = JsonUtils.toJson(response);
        System.out.println(result);
        TestCase.assertEquals("{\"message\":\"success\"}", result);
        Thread.sleep(2000);
    }

    @Test
    public void test005_search() throws Exception {
        CacheClusterViewer cacheClusterViewer = CacheClusterViewerFactory.getCacheClusterViewer();
        CacheGroupClient cacheGroupClient = new CacheGroupClient(cacheClusterViewer);
        TestCacheEntry testCacheEntry = new TestCacheEntry();
        testCacheEntry.setField1("field1");
        testCacheEntry.setField2("field2");
        CacheSearchResponse response = cacheGroupClient.search("cache_group_test1", testCacheEntry);
        String result = JsonUtils.toJson(response);
        System.out.println(result);
        TestCase.assertEquals("{\"scores\":[1.0],\"entries\":[{\"field1\":\"field1\",\"field2\":\"field2\"}]}", result);
        Thread.sleep(2000);
    }

    @Test
    public void test006_delete() throws Exception {
        CacheClusterViewer cacheClusterViewer = CacheClusterViewerFactory.getCacheClusterViewer();
        CacheGroupClient cacheGroupClient = new CacheGroupClient(cacheClusterViewer);
        CacheGroupDeleteResponse response = cacheGroupClient.delete("cache_group_test1");
        String result = JsonUtils.toJson(response);
        System.out.println(result);
        TestCase.assertEquals("{\"message\":\"success\"}", result);
        Thread.sleep(2000);
    }

}