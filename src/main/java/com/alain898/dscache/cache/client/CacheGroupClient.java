package com.alain898.dscache.cache.client;

import com.alain898.dscache.api.rest.response.RestDeleteCacheGroupResponse;
import com.alain898.dscache.cache.*;
import com.alain898.dscache.cache.client.response.*;
import com.google.common.base.Preconditions;
import com.alain898.dscache.api.rest.request.RestCreateCacheGroupRequest;
import com.alain898.dscache.api.rest.request.RestDeleteCacheGroupRequest;
import com.alain898.dscache.api.rest.request.RestUpdateCacheGroupRequest;
import com.alain898.dscache.api.rest.response.RestCreateCacheGroupResponse;
import com.alain898.dscache.api.rest.response.RestUpdateCacheGroupResponse;
import com.alain898.dscache.cache.client.exceptions.CacheClientException;
import com.alain898.dscache.common.http.HttpClient;
import com.alain898.dscache.common.partitioner.HashPartitioner;
import com.alain898.dscache.common.partitioner.IPartitioner;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * Created by alain on 16/9/5.
 */
public class CacheGroupClient {
    private CacheClusterViewer cacheClusterViewer;
    private CacheClient cacheClient;

    public CacheGroupClient(CacheClusterViewer cacheClusterViewer) {
        this.cacheClusterViewer = cacheClusterViewer;
        this.cacheClient = new CacheClient(cacheClusterViewer);
    }

    public CacheSearchResponse search(String cacheGroupName, ICacheEntry entry) {
        Preconditions.checkArgument(StringUtils.isNotBlank(cacheGroupName), "cacheGroupName is blank");
        Preconditions.checkNotNull(entry, "entry is blank");

        CacheGroupMeta cacheGroupMeta = cacheClusterViewer.getCacheGroupMeta(cacheGroupName);
        if (cacheGroupMeta == null) {
            throw new CacheClientException(String.format("can not find cache group by name[%s]", cacheGroupMeta));
        }

        String key = entry.key();
        IPartitioner partitioner = new HashPartitioner(cacheGroupMeta.getCacheGroupCapacity());
        int partition = partitioner.getPartition(key);
        int cacheIndex = partition % cacheGroupMeta.getCurrentCachesNumber();
        List<CacheMeta> cacheMetaList = cacheGroupMeta.getCacheMetas();
        CacheMeta cacheMeta = cacheMetaList.get(cacheIndex);
        return cacheClient.search(cacheMeta.getName(), entry);
    }

    public CacheSaveResponse save(String cacheGroupName, ICacheEntry entry) {
        Preconditions.checkArgument(StringUtils.isNotBlank(cacheGroupName), "cacheGroupName is blank");
        Preconditions.checkNotNull(entry, "entry is null");

        CacheGroupMeta cacheGroupMeta = cacheClusterViewer.getCacheGroupMeta(cacheGroupName);
        if (cacheGroupMeta == null) {
            throw new CacheClientException(String.format("cannot find cache group by name[%s]", cacheGroupName));
        }

        String key = entry.key();
        IPartitioner partitioner = new HashPartitioner(cacheGroupMeta.getCacheGroupCapacity());
        int partition = partitioner.getPartition(key);
        int cacheIndex = partition % cacheGroupMeta.getCurrentCachesNumber();
        List<CacheMeta> cacheMetaList = cacheGroupMeta.getCacheMetas();
        CacheMeta cacheMeta = cacheMetaList.get(cacheIndex);
        return cacheClient.save(cacheMeta.getName(), entry);
    }

    public CreateCacheGroupResponse create(String cacheGroupName,
                                           String entryClassName,
                                           int cacheGroupCapacity,
                                           int cachesNumber,
                                           int subCachesPerCache,
                                           int partitionsPerSubCache,
                                           int blockCapacity,
                                           int blocksPerPartition) {
        Preconditions.checkArgument(StringUtils.isNotBlank(cacheGroupName), "cacheGroupName is blank");
        Preconditions.checkArgument(StringUtils.isNotBlank(entryClassName), "entryClassName is blank");
        Preconditions.checkArgument(Validator.isValidCachesNumber(cacheGroupCapacity), "cacheGroupCapacity must be power of two");
        Preconditions.checkArgument(Validator.isValidCachesNumber(cachesNumber), "cachesNumber must be power of two");
        Preconditions.checkArgument(Validator.isValidSubCachesNumber(subCachesPerCache), "subCachesPerCache must be positive");
        Preconditions.checkArgument(Validator.isValidPartitions(partitionsPerSubCache), "partitionsPerSubCache must be positive");
        Preconditions.checkArgument(Validator.isValidBlockCapacity(blockCapacity), "blockCapacity must be positive");
        Preconditions.checkArgument(Validator.isValidBlocks(blocksPerPartition), "blocksPerPartition must be positive");
        Preconditions.checkArgument(cacheGroupCapacity >= cachesNumber, "cacheGroupCapacity must be greater than cachesNumber");


        Host host = cacheClusterViewer.getHosts().get(0);
        String url = String.format("http://%s:%d", host.getHost(), host.getPort());
        String path = "/management/cache_group/create";
        HttpClient httpClient = new HttpClient();
        RestCreateCacheGroupRequest request = new RestCreateCacheGroupRequest();
        request.setCacheGroupName(cacheGroupName);
        request.setEntryClassName(entryClassName);
        request.setCacheGroupCapacity(cacheGroupCapacity);
        request.setCachesNumber(cachesNumber);
        request.setSubCachesPerCache(subCachesPerCache);
        request.setPartitionsPerSubCache(partitionsPerSubCache);
        request.setBlockCapacity(blockCapacity);
        request.setBlocksPerPartition(blocksPerPartition);

        RestCreateCacheGroupResponse response =
                httpClient.post(url, path, request, RestCreateCacheGroupResponse.class);
        return new CreateCacheGroupResponse(response.getMessage());
    }

    public CacheGroupUpdateResponse update(String cacheGroupName,
                                           int addedCaches) {
        Preconditions.checkArgument(StringUtils.isNotBlank(cacheGroupName), "cacheGroupName is blank");
        Preconditions.checkArgument(addedCaches >= 0, "addedCaches must be positive");

        CacheGroupMeta cacheGroupMeta = cacheClusterViewer.getCacheGroupMeta(cacheGroupName);
        if (cacheGroupMeta == null) {
            throw new CacheClientException(String.format("can not find cache group by name[%s]", cacheGroupMeta));
        }

        Host host = cacheClusterViewer.getHosts().get(0);

        String url = String.format("http://%s:%d", host.getHost(), host.getPort());
        String path = "/management/cache_group/update";
        HttpClient httpClient = new HttpClient();
        RestUpdateCacheGroupRequest request = new RestUpdateCacheGroupRequest();
        request.setCacheGroupName(cacheGroupName);
        request.setAddedCaches(addedCaches);

        RestUpdateCacheGroupResponse response =
                httpClient.post(url, path, request, RestUpdateCacheGroupResponse.class);
        return new CacheGroupUpdateResponse(response.getMessage());
    }

    public CacheGroupDeleteResponse delete(String cacheGroupName) throws Exception {
        Preconditions.checkArgument(StringUtils.isNotBlank(cacheGroupName), "cacheGroupName is blank");

        Host host = cacheClusterViewer.getHosts().get(0);

        String url = String.format("http://%s:%d", host.getHost(), host.getPort());
        String path = "/management/cache_group/delete";
        HttpClient httpClient = new HttpClient();
        RestDeleteCacheGroupRequest request = new RestDeleteCacheGroupRequest();
        request.setCacheGroupName(cacheGroupName);

        RestDeleteCacheGroupResponse response =
                httpClient.post(url, path, request, RestDeleteCacheGroupResponse.class);
        return new CacheGroupDeleteResponse(response.getMessage());
    }
}
