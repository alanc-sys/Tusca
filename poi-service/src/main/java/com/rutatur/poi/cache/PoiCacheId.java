package com.rutatur.poi.cache;

import java.util.Objects;

/** Composite primary key of a cached POI row. */
public class PoiCacheId implements java.io.Serializable {

    private String queryKey;
    private String externalId;

    public PoiCacheId() {
    }

    public PoiCacheId(String queryKey, String externalId) {
        this.queryKey = queryKey;
        this.externalId = externalId;
    }

    public String getQueryKey() {
        return queryKey;
    }

    public void setQueryKey(String queryKey) {
        this.queryKey = queryKey;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PoiCacheId that)) {
            return false;
        }
        return Objects.equals(queryKey, that.queryKey) && Objects.equals(externalId, that.externalId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(queryKey, externalId);
    }
}
