package com.rahbar.config;

import com.rahbar.service.ErrorAlertService;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * Background jobs (reminders, scheduled broadcasts, installments, alert emails) run on two threads; a job that
 * crashes is logged and reported through ErrorAlertService instead of only appearing in the log.
 */
@Configuration
public class SchedulingConfig implements SchedulingConfigurer {

    private final ErrorAlertService alerts;

    public SchedulingConfig(ErrorAlertService alerts) {
        this.alerts = alerts;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("rahbar-job-");
        scheduler.setErrorHandler(e -> alerts.record(e, "Background job"));
        scheduler.initialize();
        registrar.setTaskScheduler(scheduler);
    }
}
