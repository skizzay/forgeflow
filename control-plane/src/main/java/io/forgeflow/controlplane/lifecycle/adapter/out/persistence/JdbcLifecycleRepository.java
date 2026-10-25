package io.forgeflow.controlplane.lifecycle.adapter.out.persistence;

import io.forgeflow.controlplane.lifecycle.application.port.out.LifecycleRepository;
import io.forgeflow.controlplane.lifecycle.domain.AuthorityFacts;
import io.forgeflow.controlplane.lifecycle.domain.EntityKind;
import io.forgeflow.controlplane.lifecycle.domain.EntityRef;
import io.forgeflow.controlplane.lifecycle.domain.EpicState;
import io.forgeflow.controlplane.lifecycle.domain.JobState;
import io.forgeflow.controlplane.lifecycle.domain.LifecycleEntity;
import io.forgeflow.controlplane.lifecycle.domain.LifecycleException;
import io.forgeflow.controlplane.lifecycle.domain.LifecycleState;
import io.forgeflow.controlplane.lifecycle.domain.ParentContext;
import io.forgeflow.controlplane.lifecycle.domain.RecordedTransition;
import io.forgeflow.controlplane.lifecycle.domain.StoryState;
import io.forgeflow.controlplane.lifecycle.domain.TransitionCommand;
import io.forgeflow.controlplane.lifecycle.domain.TransitionReceipt;
import io.forgeflow.controlplane.lifecycle.domain.approval.ApprovalIdentity;
import io.forgeflow.controlplane.lifecycle.domain.epic.Epic;
import io.forgeflow.controlplane.lifecycle.domain.job.AttemptIdentity;
import io.forgeflow.controlplane.lifecycle.domain.job.ExecutionIdentity;
import io.forgeflow.controlplane.lifecycle.domain.job.Job;
import io.forgeflow.controlplane.lifecycle.domain.story.MergeTarget;
import io.forgeflow.controlplane.lifecycle.domain.story.Story;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

public final class JdbcLifecycleRepository implements LifecycleRepository {
    private final JdbcTemplate jdbc;
    private final JsonMapper mapper = JsonMapper.builder().build();
    public JdbcLifecycleRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public LifecycleEntity lock(EntityRef entity) {
        return access(() -> {
            // Coarse root-to-leaf locks serialize transitions within an epic, including completion.
            if (entity.kind() == EntityKind.JOB) {
                var parents = one("select s.epic_id,s.id story_id from job j join workflow_run r on r.id=j.run_id "
                        + "join story s on s.id=r.story_id where j.id=?", entity.id());
                one("select id from epic where id=? for update", parents.get("epic_id"));
                one("select id from story where id=? for update", parents.get("story_id"));
            } else if (entity.kind() == EntityKind.STORY) {
                var parent = one("select epic_id from story where id=?", entity.id());
                one("select id from epic where id=? for update", parent.get("epic_id"));
            }
            String table = table(entity.kind());
            var row = one("select * from " + table + " where id=? for update", entity.id());
            var state = LifecycleState.parse(entity.kind(), text(row, "state"));
            ApprovalIdentity approval = null;
            ExecutionIdentity execution = null;
            MergeTarget target = null;
            boolean allMerged = false;
            boolean parentActive = true;
            ApprovalIdentity parentApproval = null;
            if (entity.kind() == EntityKind.JOB) {
                var run = one("select * from workflow_run where id=?", row.get("run_id"));
                var parents = one("select s.state story_state,e.state epic_state,s.approved_identity story_approval,e.approved_identity epic_approval "
                        + "from story s join epic e on e.id=s.epic_id where s.id=?",run.get("story_id"));
                parentActive = "ACTIVE".equals(parents.get("story_state")) && "ACTIVE".equals(parents.get("epic_state"));
                var storyApproval = approval(parents.get("story_approval"));
                var epicApproval = approval(parents.get("epic_approval"));
                parentApproval = Objects.equals(storyApproval,epicApproval) ? storyApproval : null;
                approval = new ApprovalIdentity((UUID) run.get("approval_id"), text(run,"plan_revision"), text(run,"configuration_sha256"));
                execution = new ExecutionIdentity((UUID) run.get("id"), approval, text(run,"workflow_key"), number(run,"workflow_version").intValue(),
                        text(run,"process_definition_id"), text(row,"activity_key"), number(row,"activity_version").intValue());
            } else {
                approval = approval(row.get("approved_identity"));
                if (entity.kind() == EntityKind.STORY) {
                    var binding = one("select r.github_id,e.branch_name,e.state epic_state,e.approved_identity parent_approval from repository r join epic e on e.repository_id=r.id where e.id=?", row.get("epic_id"));
                    parentActive = "ACTIVE".equals(binding.get("epic_state"));
                    parentApproval = approval(binding.get("parent_approval"));
                    int pr = row.get("pull_request_number") == null ? 0 : number(row,"pull_request_number").intValue();
                    target = new MergeTarget(number(binding,"github_id").longValue(), pr, text(binding,"branch_name"), text(row,"expected_pr_head_sha"));
                } else {
                    allMerged = Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from story where epic_id=?) "
                            + "and not exists(select 1 from story where epic_id=? and state<>'MERGED')", Boolean.class, entity.id(), entity.id()));
                }
            }
            long version = number(row, "state_version").longValue();
            return switch (state) {
                case JobState job -> new Job(entity, job, version, approval, execution, new ParentContext(parentActive, parentApproval));
                case StoryState story -> new Story(entity, story, version, approval, target, new ParentContext(parentActive, parentApproval));
                case EpicState epic -> new Epic(entity, epic, version, approval, allMerged);
            };
        });
    }

    @Override
    public Optional<RecordedTransition> findTransition(UUID commandId) {
        return access(() -> jdbc.query("select * from lifecycle_transition where command_id=?", (row,index) -> {
            EntityKind kind = EntityKind.valueOf(row.getString("entity_kind"));
            var receipt = new TransitionReceipt(commandId, new EntityRef(kind, row.getObject("entity_id", UUID.class)),
                    LifecycleState.parse(kind,row.getString("from_state")), LifecycleState.parse(kind,row.getString("to_state")), row.getLong("resulting_version"));
            return new RecordedTransition(row.getString("request_sha256"), row.getString("actor_id"), receipt);
        },commandId).stream().findFirst());
    }

    @Override
    public boolean ownsLiveAttempt(UUID jobId, AttemptIdentity attempt, Instant now) {
        return access(() -> Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from job_attempt where job_id=? and id=? "
                + "and worker_id=? and fencing_token=? and finished_at is null and lease_expires_at>?)", Boolean.class,
                jobId,attempt.attemptId(),attempt.workerId(),attempt.fencingToken(),Timestamp.from(now))));
    }

    @Override
    public boolean hasLiveAttempt(UUID jobId, Instant now) {
        return access(() -> Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from job_attempt where job_id=? "
                + "and finished_at is null and lease_expires_at>?)", Boolean.class,jobId,Timestamp.from(now))));
    }

    @Override
    public boolean hasSuccessfulJob(UUID storyId, ApprovalIdentity approval) {
        return access(() -> Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from job j join workflow_run r on r.id=j.run_id "
                + "where r.story_id=? and j.state='SUCCEEDED' and r.approval_id=? and r.plan_revision=? and r.configuration_sha256=?)",
                Boolean.class,storyId,approval.approvalId(),approval.planRevision(),approval.configurationSha256())));
    }

    @Override
    public void save(LifecycleEntity before, LifecycleEntity after, TransitionCommand command, AuthorityFacts facts, TransitionReceipt receipt, String digest, Instant now) {
        access(() -> {
            String table = table(command.entity().kind());
            int changed;
            ApprovalIdentity pin = after.approval();
            if (command.entity().kind() == EntityKind.JOB) {
                changed = jdbc.update("update job set state=?,state_version=? where id=? and state_version=?",
                        after.state().name(),after.version(),command.entity().id(),before.version());
                if (command.nextState() == JobState.SUCCEEDED || command.nextState() == JobState.FAILED
                        || command.nextState() == JobState.CANCELLED || (before.state() == JobState.RECONCILING && command.nextState() == JobState.PENDING)) {
                    jdbc.update("update job_attempt set finished_at=? where job_id=? and finished_at is null",Timestamp.from(now),command.entity().id());
                }
            } else {
                String identity = pin == null ? null : mapper.writeValueAsString(pin);
                if (command.nextState() == StoryState.MERGED) {
                    changed = jdbc.update("update story set state='MERGED',state_version=?,approved_identity=cast(? as jsonb),merged_commit_sha=? "
                            + "where id=? and state_version=?",after.version(),identity,facts.delivery().pullRequest().mergeCommitSha(),command.entity().id(),before.version());
                } else {
                    changed = jdbc.update("update " + table + " set state=?,state_version=?,approved_identity=cast(? as jsonb) where id=? and state_version=?",
                            after.state().name(),after.version(),identity,command.entity().id(),before.version());
                }
            }
            if (changed != 1) throw new LifecycleException(LifecycleException.Kind.STALE_STATE);
            var identity = new LinkedHashMap<String,Object>();
            identity.put("approval",pin != null ? pin : before.approval()); identity.put("execution",before instanceof Job job ? job.execution() : null); identity.put("attempt",command.attempt());
            identity.put("finalHumanApprovalId",facts.completion().finalHumanApprovalId());
            if (facts.delivery().pullRequest() != null) identity.put("pullRequest",facts.delivery().pullRequest());
            jdbc.update("insert into lifecycle_transition(command_id,request_sha256,entity_kind,entity_id,from_state,to_state,resulting_version,actor_id,identity,occurred_at) "
                    + "values (?,?,?,?,?,?,?,?,cast(? as jsonb),?)",command.commandId(),digest,command.entity().kind().name(),command.entity().id(),
                    before.state().name(),command.nextState().name(),receipt.version(),facts.actor().actorId(),mapper.writeValueAsString(identity),Timestamp.from(now));
            return null;
        });
    }

    private ApprovalIdentity approval(Object json) { return json == null ? null : mapper.readValue(json.toString(), ApprovalIdentity.class); }
    private Map<String,Object> one(String sql,Object id) {
        var rows = jdbc.queryForList(sql,id);
        if (rows.isEmpty()) throw new LifecycleException(LifecycleException.Kind.NOT_FOUND);
        return rows.getFirst();
    }
    private static String table(EntityKind kind) { return switch (kind) { case JOB -> "job"; case STORY -> "story"; case EPIC -> "epic"; }; }
    private static String text(Map<String,Object> row,String key) { return (String)row.get(key); }
    private static Number number(Map<String,Object> row,String key) { return (Number)row.get(key); }
    private static <T> T access(Supplier<T> work) {
        try { return work.get(); }
        catch (DataAccessException failure) { throw new LifecycleException(LifecycleException.Kind.STORAGE_UNAVAILABLE); }
    }
}
