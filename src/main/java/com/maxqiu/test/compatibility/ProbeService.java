package com.maxqiu.test.compatibility;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class ProbeService {
    private final ProbeMapper mapper;
    private final JdbcTemplate jdbc;

    public ProbeService(ProbeMapper mapper, JdbcTemplate jdbc) {
        this.mapper = mapper;
        this.jdbc = jdbc;
    }

    public List<ProbeRow> seed(String key) {
        // Schema setup is explicit fixture preparation, not an application startup dependency.
        new ResourceDatabasePopulator(new ClassPathResource("db/native-probes.sql"))
                .execute(Objects.requireNonNull(jdbc.getDataSource()));
        delete(key);
        ProbeRow.Phase[] phases = ProbeRow.Phase.values();
        for (int index = 1; index <= 6; index++) {
            mapper.insert(new ProbeRow().setRunKey(key).setOrdinalIndex(index)
                    .setPhase(phases[(index - 1) % phases.length]));
        }
        return list(key);
    }

    public List<ProbeRow> list(String key) {
        return mapper.selectList(Wrappers.<ProbeRow>lambdaQuery()
                .eq(ProbeRow::getRunKey, key).orderByAsc(ProbeRow::getOrdinalIndex));
    }

    public Page<ProbeRow> page(String key, long page, long size, ProbeRow.Phase phase) {
        return mapper.selectPage(new Page<>(page, size), Wrappers.<ProbeRow>lambdaQuery()
                .eq(ProbeRow::getRunKey, key).eq(phase != null, ProbeRow::getPhase, phase)
                .orderByAsc(ProbeRow::getOrdinalIndex));
    }

    public List<Integer> storedCodes(String key) {
        return jdbc.queryForList("SELECT phase FROM native_probe_row WHERE run_key = ? ORDER BY ordinal_index",
                Integer.class, key);
    }

    public void changeFirstPhase(String key, ProbeRow.Phase phase) {
        jdbc.update("UPDATE native_probe_row SET phase = ? WHERE run_key = ? AND ordinal_index = 1",
                phase.getCode(), key);
    }

    public void delete(String key) {
        mapper.delete(Wrappers.<ProbeRow>lambdaQuery().eq(ProbeRow::getRunKey, key));
    }
}
