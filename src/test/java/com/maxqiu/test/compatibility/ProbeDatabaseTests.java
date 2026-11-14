package com.maxqiu.test.compatibility;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import com.maxqiu.test.NativeImageTestApplication;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = NativeImageTestApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:native-probes;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.cache.type=simple"})
class ProbeDatabaseTests {
    @Autowired
    ProbeService service;
    @Autowired
    ProbeCache cache;
    @Autowired
    ApplicationContext context;

    String key;

    @BeforeEach
    void seed() {
        key = UUID.randomUUID().toString();
        service.seed(key);
    }

    @AfterEach
    void clean() {
        cache.evict(key);
        service.delete(key);
    }

    @Test
    void paginationRunsCountAndLimitQueriesAgainstIndependentNumberedRows() {
        var second = service.page(key, 2, 2, null);
        assertThat(second.getTotal()).isEqualTo(6);
        assertThat(second.getPages()).isEqualTo(3);
        assertThat(second.getRecords()).extracting(ProbeRow::getOrdinalIndex).containsExactly(3, 4);
        assertThat(service.page(key, 4, 2, null).getRecords()).isEmpty();
        assertThat(context.getBean(ProbeMapper.class)).isNotNull();
    }

    @Test
    void enumValuesUseExplicitDatabaseCodesAndSurviveSelectAndLambdaFiltering() {
        assertThat(service.storedCodes(key)).containsExactly(10, 20, 30, 10, 20, 30);
        var beta = service.page(key, 1, 10, ProbeRow.Phase.BETA);
        assertThat(beta.getTotal()).isEqualTo(2);
        assertThat(beta.getRecords()).extracting(ProbeRow::getOrdinalIndex).containsExactly(2, 5);
        assertThat(beta.getRecords()).extracting(ProbeRow::getPhase).containsOnly(ProbeRow.Phase.BETA);
    }

    @Test
    void cacheHitRetainsSnapshotUntilEvictedInsteadOfReadingChangedDatabaseRows() {
        assertThat(cache.snapshot(key).getFirst().getPhase()).isEqualTo(ProbeRow.Phase.ALPHA);
        service.changeFirstPhase(key, ProbeRow.Phase.GAMMA);
        assertThat(service.list(key).getFirst().getPhase()).isEqualTo(ProbeRow.Phase.GAMMA);
        assertThat(cache.snapshot(key).getFirst().getPhase()).isEqualTo(ProbeRow.Phase.ALPHA);
        assertThat(cache.loads(key)).isEqualTo(1);
        cache.evict(key);
        assertThat(cache.snapshot(key).getFirst().getPhase()).isEqualTo(ProbeRow.Phase.GAMMA);
        assertThat(cache.loads(key)).isEqualTo(2);
    }
}
