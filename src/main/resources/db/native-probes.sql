CREATE TABLE IF NOT EXISTS native_probe_row (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_key VARCHAR(64) NOT NULL,
    ordinal_index INT NOT NULL,
    phase INT NOT NULL,
    UNIQUE (run_key, ordinal_index)
);
