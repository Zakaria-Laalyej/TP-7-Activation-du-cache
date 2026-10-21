package com.example.cache;

import org.hibernate.boot.spi.SessionFactoryOptions;
import org.hibernate.cache.cfg.spi.DomainDataRegionBuildingContext;
import org.hibernate.cache.cfg.spi.DomainDataRegionConfig;
import org.hibernate.cache.spi.support.DomainDataStorageAccess;
import org.hibernate.cache.spi.support.RegionFactoryTemplate;
import org.hibernate.cache.spi.support.StorageAccess;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation autonome et complete du cache de second niveau Hibernate 5.6.
 * Fournit le stockage en memoire inter-sessions sans dependance externe.
 */
public class SimpleInMemoryRegionFactory extends RegionFactoryTemplate {

    private final Map<String, StorageAccess> storageMap = new ConcurrentHashMap<>();

    @Override
    protected void prepareForUse(SessionFactoryOptions options, Map properties) {
        // Initialisation si necessaire
    }

    @Override
    protected void releaseFromUse() {
        storageMap.clear();
    }

    @Override
    protected DomainDataStorageAccess createDomainDataStorageAccess(
            DomainDataRegionConfig regionConfig,
            DomainDataRegionBuildingContext buildingContext) {
        return (DomainDataStorageAccess) storageMap.computeIfAbsent(
                regionConfig.getRegionName(),
                name -> new SimpleDomainStorageAccess()
        );
    }

    @Override
    protected StorageAccess createQueryResultsRegionStorageAccess(
            String regionName,
            SessionFactoryImplementor sessionFactory) {
        return storageMap.computeIfAbsent(regionName, name -> new SimpleBasicStorageAccess());
    }

    @Override
    protected StorageAccess createTimestampsRegionStorageAccess(
            String regionName,
            SessionFactoryImplementor sessionFactory) {
        return storageMap.computeIfAbsent(regionName, name -> new SimpleBasicStorageAccess());
    }

    public static class SimpleBasicStorageAccess implements StorageAccess {
        protected final Map<Object, Object> data = new ConcurrentHashMap<>();

        @Override
        public Object getFromCache(Object key, SharedSessionContractImplementor session) {
            return data.get(key);
        }

        @Override
        public void putIntoCache(Object key, Object value, SharedSessionContractImplementor session) {
            data.put(key, value);
        }

        @Override
        public boolean contains(Object key) {
            return data.containsKey(key);
        }

        @Override
        public void evictData(Object key) {
            data.remove(key);
        }

        @Override
        public void evictData() {
            data.clear();
        }

        @Override
        public void release() {
            data.clear();
        }
    }

    public static class SimpleDomainStorageAccess extends SimpleBasicStorageAccess implements DomainDataStorageAccess {
        @Override
        public void putFromLoad(Object key, Object value, SharedSessionContractImplementor session) {
            data.put(key, value);
        }
    }
}
