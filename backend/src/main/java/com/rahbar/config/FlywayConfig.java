package com.rahbar.config;

import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Set;

/**
 * Database upgrades at start-up. MySQL can't roll back a half-run script, so when an upgrade script failed on an
 * earlier start, Flyway marks it failed and refuses to start again until someone runs "flyway repair" on the server.
 * Here that failed mark is cleared first (only then), so the corrected script runs again on the next deploy. Our
 * scripts check before every change, so running one again after a half-run is safe.
 */
@Configuration
public class FlywayConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywayConfig.class);

    /**
     * Scripts corrected after release because they failed on the live database. A database where the first version did
     * run (a test copy) has its recorded checksum updated instead of refusing to start. Never add a script that worked
     * on the live database here: write a new V&lt;next&gt; instead.
     */
    static final Set<String> CORRECTED = Set.of("47");

    @Bean
    public FlywayMigrationStrategy repairFailedThenMigrate() {
        return flyway -> {
            MigrationInfo[] failed = Arrays.stream(flyway.info().all())
                    .filter(m -> m.getState() == MigrationState.FAILED).toArray(MigrationInfo[]::new);
            for (MigrationInfo m : failed) {
                log.warn("Database upgrade V{} ({}) failed on an earlier start: clearing its failed mark and running it again.",
                        m.getVersion(), m.getDescription());
            }
            boolean repair = failed.length > 0;
            if (!repair) {
                var result = flyway.validateWithResult();
                repair = !result.validationSuccessful && !result.invalidMigrations.isEmpty()
                        && result.invalidMigrations.stream().allMatch(m -> CORRECTED.contains(m.version));
                if (repair) log.warn("Database upgrade(s) {} were corrected after release: updating their recorded checksums.", CORRECTED);
            }
            if (repair) flyway.repair();
            flyway.migrate();
        };
    }
}
