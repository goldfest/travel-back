package com.travelapp.route.service;

import com.travelapp.route.model.entity.Route;

public interface RouteNotificationService {
    void notifyRouteCreated(Route route);
    void rescheduleOptimizedRouteNotifications(Route route);
    void deleteRouteNotifications(Route route);
}
