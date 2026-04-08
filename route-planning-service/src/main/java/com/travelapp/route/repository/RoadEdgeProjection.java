package com.travelapp.route.repository;

public interface RoadEdgeProjection {
    Long getEdgeId();
    Long getFromNodeId();
    Double getFromLatitude();
    Double getFromLongitude();
    Long getToNodeId();
    Double getToLatitude();
    Double getToLongitude();
    Double getLengthM();
    Boolean getWalkAllowed();
    Boolean getCarAllowed();
    Boolean getMixedAllowed();
    Boolean getPublicTransportAllowed();
    Integer getWalkTimeSec();
    Integer getCarTimeSec();
    Integer getMixedTimeSec();
    Integer getPublicTransportTimeSec();
    Boolean getBidirectional();
    String getPolylineJson();
}
