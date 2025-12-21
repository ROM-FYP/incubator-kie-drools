package org.drools.benchmark.waltzdb;

public interface ClusterAware {
    String getClusterId();
    void setClusterId(String clusterId);
}
