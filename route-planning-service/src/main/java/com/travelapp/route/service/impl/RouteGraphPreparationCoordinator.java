package com.travelapp.route.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class RouteGraphPreparationCoordinator {

    private final RouteGraphPreparationWorker worker;

    public void scheduleAfterCommit(Long routeId, Long cityId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            worker.prepareGraphAndRoute(routeId, cityId);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                worker.prepareGraphAndRoute(routeId, cityId);
            }
        });
    }
}
