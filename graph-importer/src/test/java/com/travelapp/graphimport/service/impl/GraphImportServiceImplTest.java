package com.travelapp.graphimport.service.impl;

import org.junit.jupiter.api.Test;

class GraphImportServiceImplTest {

    @Test
    void placeholder_for_import_failure_status_and_safe_osm_path() {
        // TODO: покрыть сценарии:
        // 1) путь вне /osm-data -> IllegalArgumentException
        // 2) ошибка импорта -> graph version переводится в FAILED
        // 3) успешный импорт -> ACTIVE version + cleanup archived versions
    }
}
