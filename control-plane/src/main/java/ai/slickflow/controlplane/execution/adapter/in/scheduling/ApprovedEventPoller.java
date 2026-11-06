package ai.slickflow.controlplane.execution.adapter.in.scheduling;

import ai.slickflow.controlplane.execution.application.port.in.DispatchApprovedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

public final class ApprovedEventPoller {
    private static final Logger LOG = LoggerFactory.getLogger(ApprovedEventPoller.class);
    private final DispatchApprovedEvent events;
    public ApprovedEventPoller(DispatchApprovedEvent events) { this.events = events; }
    @Scheduled(fixedDelayString = "${slickflow.execution.inbox.poll-interval:PT1S}",
            initialDelayString = "${slickflow.execution.inbox.poll-interval:PT1S}")
    public void poll() {
        try {
            for (int count = 0; count < 100 && events.dispatchOne(); count++) { /* bounded batch */ }
        } catch (RuntimeException failure) {
            LOG.warn("Approved event dispatch deferred; pending delivery retained for retry");
        }
    }
}
