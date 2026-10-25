package io.forgeflow.controlplane;

import io.forgeflow.controlplane.lifecycle.adapter.out.persistence.JdbcLifecycleRepository;
import io.forgeflow.controlplane.lifecycle.adapter.out.persistence.SpringLifecycleTransactions;
import io.forgeflow.controlplane.lifecycle.application.LifecycleService;
import io.forgeflow.controlplane.lifecycle.application.port.in.ChangeLifecycle;
import io.forgeflow.controlplane.lifecycle.domain.AuthorityFacts;
import io.forgeflow.controlplane.lifecycle.domain.EntityKind;
import io.forgeflow.controlplane.lifecycle.domain.EntityRef;
import io.forgeflow.controlplane.lifecycle.domain.EpicState;
import io.forgeflow.controlplane.lifecycle.domain.JobState;
import io.forgeflow.controlplane.lifecycle.domain.LifecycleException;
import io.forgeflow.controlplane.lifecycle.domain.LifecycleState;
import io.forgeflow.controlplane.lifecycle.domain.StoryState;
import io.forgeflow.controlplane.lifecycle.domain.TransitionCommand;
import io.forgeflow.controlplane.lifecycle.domain.approval.ApprovalIdentity;
import io.forgeflow.controlplane.lifecycle.domain.evidence.ActorAuthority;
import io.forgeflow.controlplane.lifecycle.domain.evidence.CompletionEvidence;
import io.forgeflow.controlplane.lifecycle.domain.evidence.DeliveryEvidence;
import io.forgeflow.controlplane.lifecycle.domain.evidence.RecoveryEvidence;
import io.forgeflow.controlplane.lifecycle.domain.job.AttemptIdentity;
import io.forgeflow.controlplane.lifecycle.domain.job.ExecutionIdentity;
import io.forgeflow.controlplane.lifecycle.domain.story.MergeTarget;
import io.forgeflow.controlplane.lifecycle.domain.story.PullRequestObservation;
import java.sql.DriverManager;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.flowable.engine.ProcessEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LifecycleIntegrationTest {
    private final String user = System.getenv("FORGEFLOW_DB_USER");
    private final String password = System.getenv("FORGEFLOW_DB_PASSWORD");
    private final UUID repo = UUID.randomUUID(), epic = UUID.randomUUID(), story = UUID.randomUUID(), job = UUID.randomUUID(), run = UUID.randomUUID();
    private final UUID worker = UUID.randomUUID(), attemptId = UUID.randomUUID();
    private final ApprovalIdentity approval = new ApprovalIdentity(UUID.randomUUID(),"plan-v1","b".repeat(64));
    private final AttemptIdentity attempt = new AttemptIdentity(attemptId,worker,1);
    private final MergeTarget target = new MergeTarget(42,17,"epic/foundation","a".repeat(40));
    private final AtomicReference<AuthorityFacts> facts = new AtomicReference<>();
    private String admin,database,url,processId;
    private ConfigurableApplicationContext context;
    private JdbcTemplate jdbc;
    private ChangeLifecycle service;
    private ExecutionIdentity execution;

    @BeforeEach
    void prepare() throws Exception {
        admin = System.getenv("FORGEFLOW_DB_URL");
        assertThat(admin).as("Use a disposable PostgreSQL database via scripts/verify-bootstrap.sh").isNotBlank();
        database = "lifecycle_"+UUID.randomUUID().toString().replace("-","");
        url = admin.substring(0,admin.lastIndexOf('/')+1)+database+"?currentSchema=lifecycle_domain";
        try (var connection=DriverManager.getConnection(admin,user,password);var sql=connection.createStatement()) { sql.execute("create database "+database); }
        var source=new DriverManagerDataSource(url,user,password);
        DatabaseMigrator.migrate(source,DatabaseSchemas.fromJdbcUrl(url,"lifecycle_engine"));
        restart();
        String identity=JsonMapper.builder().build().writeValueAsString(approval);
        jdbc.update("insert into repository(id,github_id,full_name) values (?,42,'public/fixture')",repo);
        jdbc.update("insert into epic(id,repository_id,issue_number,state,branch_name,approved_identity) values (?,?,1,'ACTIVE','epic/foundation',cast(? as jsonb))",epic,repo,identity);
        jdbc.update("insert into story(id,epic_id,repository_id,issue_number,state,pull_request_number,expected_pr_head_sha,approved_identity) values (?,?,?,14,'ACTIVE',17,?,cast(? as jsonb))",story,epic,repo,target.headSha(),identity);
        String definition=jdbc.queryForObject("select definition_id from workflow_definition",String.class);
        processId=context.getBean(ProcessEngine.class).getRuntimeService().startProcessInstanceById(definition).getId();
        jdbc.update("insert into workflow_run(id,story_id,workflow_key,workflow_version,process_definition_id,process_instance_id,plan_revision,configuration_sha256,approval_id,approved_by,approved_at) "
                +"values (?,?,'bootstrap',1,?,?,?,?,?,'fixture-not-human-authentication',now())",run,story,definition,processId,approval.planRevision(),approval.configurationSha256(),approval.approvalId());
        jdbc.update("insert into activity_definition(activity_key,registry_version,input_contract_version,output_contract_version) values ('coding-test',1,1,1)");
        jdbc.update("insert into job(id,run_id,activity_key,activity_version,idempotency_key) values (?,?,'coding-test',1,?)",job,run,UUID.randomUUID().toString());
        jdbc.update("insert into worker(id,logical_name) values (?,'fixture-worker')",worker);
        jdbc.update("insert into job_attempt(id,job_id,attempt_number,worker_id,fencing_token,lease_expires_at) values (?,?,1,?,1,now()+interval '5 minutes')",attemptId,job,worker);
        execution=new ExecutionIdentity(run,approval,"bootstrap",1,definition,"coding-test",1);
        facts.set(verified(approval,true,true,true,true,JobState.SUCCEEDED,new PullRequestObservation(target,true,"c".repeat(40)),true,UUID.randomUUID()));
    }
    private AuthorityFacts verified(ApprovalIdentity pin,boolean cancellation,boolean recovery,boolean dependencies,boolean quiescent,
                                    JobState outcome,PullRequestObservation observation,boolean checks,UUID finalApproval) {
        return new AuthorityFacts(new ActorAuthority("fixture-operator",pin,cancellation,recovery,worker),
                new RecoveryEvidence(quiescent,outcome), new DeliveryEvidence(dependencies,observation),
                new CompletionEvidence(checks,finalApproval));
    }
    private void restart() {
        if(context!=null)context.close();
        context=new SpringApplicationBuilder(ControlPlaneApplication.class).web(WebApplicationType.NONE).run(
                "--spring.datasource.url="+url,"--spring.datasource.username="+user,"--spring.datasource.password="+password,
                "--forgeflow.database.engine-schema=lifecycle_engine","--logging.level.root=ERROR");
        jdbc=context.getBean(JdbcTemplate.class);
        // Explicit trusted fixture adapter; production wiring uses deny-unverified authority.
        service=new LifecycleService(new JdbcLifecycleRepository(jdbc),new SpringLifecycleTransactions(context.getBean(PlatformTransactionManager.class)),(snapshot,actor)->facts.get(),Clock.systemUTC());
    }
    @AfterEach
    void cleanup() throws Exception {
        if(context!=null)context.close();
        if(database!=null)try(var connection=DriverManager.getConnection(admin,user,password);var sql=connection.createStatement()){sql.execute("drop database if exists "+database+" with (force)");}
    }
    private TransitionCommand command(EntityKind kind,UUID id,LifecycleState from,LifecycleState to,long version) {
        return new TransitionCommand(UUID.randomUUID(),new EntityRef(kind,id),"fixture-operator",from,to,version,approval,kind==EntityKind.JOB?execution:null,kind==EntityKind.JOB?attempt:null);
    }
    private TransitionCommand job(JobState from,JobState to,long version){return command(EntityKind.JOB,job,from,to,version);}
    private void rejects(TransitionCommand command,LifecycleException.Kind kind){
        assertThatThrownBy(()->service.transition(command)).isInstanceOf(LifecycleException.class)
                .extracting(error->((LifecycleException)error).kind()).isEqualTo(kind);
    }

    @Test
    void jobAndEngineCompletionCannotManufactureAStoryMerge() {
        service.transition(job(JobState.PENDING,JobState.RUNNING,0));
        service.transition(job(JobState.RUNNING,JobState.SUCCEEDED,1));
        assertThat(jdbc.queryForObject("select state from story",String.class)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("select state from epic",String.class)).isEqualTo("ACTIVE");
        var engine=context.getBean(ProcessEngine.class);
        assertThat(engine.getRuntimeService().getActiveActivityIds(processId)).containsExactly("awaitImplementation");
        var waiting=engine.getRuntimeService().createExecutionQuery().processInstanceId(processId).activityId("awaitImplementation").singleResult();
        engine.getRuntimeService().trigger(waiting.getId());
        assertThat(engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(processId).count()).isZero();
        assertThat(jdbc.queryForObject("select state from story",String.class)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("select state from epic",String.class)).isEqualTo("ACTIVE");
        rejects(command(EntityKind.STORY,story,StoryState.ACTIVE,StoryState.MERGED,0),LifecycleException.Kind.INVALID_TRANSITION);
        service.transition(command(EntityKind.STORY,story,StoryState.ACTIVE,StoryState.PR_OPEN,0));
        facts.set(verified(approval,true,true,true,true,null,new PullRequestObservation(target,false,null),true,UUID.randomUUID()));
        rejects(command(EntityKind.STORY,story,StoryState.PR_OPEN,StoryState.MERGED,1),LifecycleException.Kind.EVIDENCE_MISSING);
        facts.set(verified(approval,true,true,true,true,null,new PullRequestObservation(target,true,"c".repeat(40)),true,UUID.randomUUID()));
        service.transition(command(EntityKind.STORY,story,StoryState.PR_OPEN,StoryState.MERGED,1));
        assertThat(jdbc.queryForObject("select merged_commit_sha from story",String.class)).isEqualTo("c".repeat(40));
        facts.set(verified(approval,true,true,true,true,null,null,true,null));
        rejects(command(EntityKind.EPIC,epic,EpicState.ACTIVE,EpicState.COMPLETED,0),LifecycleException.Kind.EVIDENCE_MISSING);
        facts.set(verified(approval,true,true,true,true,null,null,true,UUID.randomUUID()));
        service.transition(command(EntityKind.EPIC,epic,EpicState.ACTIVE,EpicState.COMPLETED,0));
    }

    @Test
    void changedApprovalExecutionOrDependencyFactsBlockDispatch() {
        facts.set(verified(new ApprovalIdentity(UUID.randomUUID(),"plan-v2","d".repeat(64)),true,true,true,true,null,null,false,null));
        rejects(job(JobState.PENDING,JobState.RUNNING,0),LifecycleException.Kind.IDENTITY_MISMATCH);
        facts.set(verified(approval,true,true,false,true,null,null,false,null));
        rejects(job(JobState.PENDING,JobState.RUNNING,0),LifecycleException.Kind.EVIDENCE_MISSING);
        facts.set(verified(approval,true,true,true,true,null,null,false,null));
        var original=job(JobState.PENDING,JobState.RUNNING,0);
        var altered=new ExecutionIdentity(run,approval,"bootstrap",2,execution.processDefinitionId(),"coding-test",1);
        rejects(new TransitionCommand(original.commandId(),original.entity(),original.actorId(),original.expectedState(),original.nextState(),0,approval,altered,attempt),LifecycleException.Kind.IDENTITY_MISMATCH);
        jdbc.update("update epic set state='CANCELLED'");
        rejects(job(JobState.PENDING,JobState.RUNNING,0),LifecycleException.Kind.EVIDENCE_MISSING);
        assertThat(jdbc.queryForObject("select count(*) from lifecycle_transition",Integer.class)).isZero();
    }

    @Test
    void staleFenceAndExpiredLeaseCannotCompleteAndRecoveryMustReconcile() {
        service.transition(job(JobState.PENDING,JobState.RUNNING,0));
        var complete=job(JobState.RUNNING,JobState.SUCCEEDED,1);
        rejects(new TransitionCommand(complete.commandId(),complete.entity(),complete.actorId(),complete.expectedState(),complete.nextState(),1,approval,execution,
                new AttemptIdentity(attemptId,worker,2)),LifecycleException.Kind.UNAUTHORIZED);
        var known = facts.get();
        var actor = known.actor();
        facts.set(new AuthorityFacts(new ActorAuthority(actor.actorId(),actor.currentApproval(),actor.cancellationAuthorized(),
                actor.recoveryAuthorized(),UUID.randomUUID()),known.recovery(),known.delivery(),known.completion()));
        rejects(complete,LifecycleException.Kind.UNAUTHORIZED); // knowing an attempt/fence does not authenticate its owner
        facts.set(known);
        rejects(job(JobState.RUNNING,JobState.RECONCILING,1),LifecycleException.Kind.UNAUTHORIZED);
        jdbc.update("update job_attempt set lease_expires_at=now()-interval '1 second'");
        rejects(complete,LifecycleException.Kind.UNAUTHORIZED);
        service.transition(job(JobState.RUNNING,JobState.RECONCILING,1));
        facts.set(verified(approval,true,true,true,false,null,null,false,null));
        rejects(job(JobState.RECONCILING,JobState.PENDING,2),LifecycleException.Kind.EVIDENCE_MISSING);
        facts.set(verified(approval,true,true,true,true,null,null,false,null));
        service.transition(job(JobState.RECONCILING,JobState.PENDING,2));
        assertThat(jdbc.queryForObject("select finished_at is not null from job_attempt",Boolean.class)).isTrue();
        rejects(job(JobState.PENDING,JobState.RUNNING,3),LifecycleException.Kind.UNAUTHORIZED);
    }

    @Test
    void mergeEvidenceMustMatchRepositoryPrBaseAndHead() {
        jdbc.update("update story set state='PR_OPEN'");
        for(var wrong:List.of(new MergeTarget(43,17,target.baseBranch(),target.headSha()),new MergeTarget(42,18,target.baseBranch(),target.headSha()),
                new MergeTarget(42,17,"main",target.headSha()),new MergeTarget(42,17,target.baseBranch(),"d".repeat(40)))){
            facts.set(verified(approval,true,true,true,true,null,new PullRequestObservation(wrong,true,"c".repeat(40)),true,UUID.randomUUID()));
            rejects(command(EntityKind.STORY,story,StoryState.PR_OPEN,StoryState.MERGED,0),LifecycleException.Kind.EVIDENCE_MISSING);
        }
        assertThat(jdbc.queryForObject("select state from story",String.class)).isEqualTo("PR_OPEN");
        assertThat(jdbc.queryForObject("select count(*) from lifecycle_transition",Integer.class)).isZero();
    }

    @Test
    void defaultAuthorityDeniesAndRevocationStillAllowsAnAuthorizedStop() {
        assertThatThrownBy(()->context.getBean(ChangeLifecycle.class).transition(job(JobState.PENDING,JobState.RUNNING,0)))
                .isInstanceOf(LifecycleException.class);
        service.transition(job(JobState.PENDING,JobState.RUNNING,0));
        facts.set(verified(null,true,false,false,false,null,null,false,null));
        rejects(job(JobState.RUNNING,JobState.SUCCEEDED,1),LifecycleException.Kind.IDENTITY_MISMATCH);
        service.transition(job(JobState.RUNNING,JobState.CANCELLED,1));
        rejects(job(JobState.CANCELLED,JobState.SUCCEEDED,2),LifecycleException.Kind.INVALID_TRANSITION);
        assertThat(jdbc.queryForObject("select state from story",String.class)).isEqualTo("ACTIVE");
    }

    @Test
    void planReapprovalIsExplicitAndStoryPinsMustMatchTheParentEpic() {
        jdbc.update("update epic set state='DRAFT',approved_identity=null");
        jdbc.update("update story set state='DRAFT',approved_identity=null");
        service.transition(command(EntityKind.EPIC,epic,EpicState.DRAFT,EpicState.APPROVED,0));
        var revised=new ApprovalIdentity(UUID.randomUUID(),"plan-v2","d".repeat(64));
        facts.set(verified(revised,true,true,true,true,null,null,false,null));
        rejects(command(EntityKind.EPIC,epic,EpicState.APPROVED,EpicState.ACTIVE,1),LifecycleException.Kind.IDENTITY_MISMATCH);
        service.transition(command(EntityKind.EPIC,epic,EpicState.APPROVED,EpicState.DRAFT,1));
        assertThat(jdbc.queryForObject("select approved_identity is null from epic",Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("select identity->'approval'->>'approvalId' from lifecycle_transition "
                + "where entity_kind='EPIC' and to_state='DRAFT'",String.class)).isEqualTo(approval.approvalId().toString());
        var approve=new TransitionCommand(UUID.randomUUID(),new EntityRef(EntityKind.EPIC,epic),"fixture-operator",EpicState.DRAFT,EpicState.APPROVED,2,revised,null,null);
        service.transition(approve);
        service.transition(new TransitionCommand(UUID.randomUUID(),approve.entity(),approve.actorId(),EpicState.APPROVED,EpicState.ACTIVE,3,revised,null,null));
        facts.set(verified(approval,true,true,true,true,null,null,false,null));
        rejects(command(EntityKind.STORY,story,StoryState.DRAFT,StoryState.READY,0),LifecycleException.Kind.IDENTITY_MISMATCH);
        facts.set(verified(revised,true,true,true,true,null,null,false,null));
        service.transition(new TransitionCommand(UUID.randomUUID(),new EntityRef(EntityKind.STORY,story),"fixture-operator",StoryState.DRAFT,StoryState.READY,0,revised,null,null));
        assertThat(jdbc.queryForObject("select approved_identity::text from story",String.class)).contains("plan-v2");
        // The original immutable job/run remains tied to v1, not to the newly approved plan.
        rejects(job(JobState.PENDING,JobState.RUNNING,0),LifecycleException.Kind.IDENTITY_MISMATCH);
    }

    @Test
    void unfinishedStoriesOrFailedIntegrationChecksBlockEpicCompletion() {
        rejects(command(EntityKind.EPIC,epic,EpicState.ACTIVE,EpicState.COMPLETED,0),LifecycleException.Kind.EVIDENCE_MISSING);
        jdbc.update("update story set state='MERGED',merged_commit_sha=?","c".repeat(40));
        facts.set(verified(approval,true,true,true,true,null,null,false,UUID.randomUUID()));
        rejects(command(EntityKind.EPIC,epic,EpicState.ACTIVE,EpicState.COMPLETED,0),LifecycleException.Kind.EVIDENCE_MISSING);
        facts.set(verified(approval,true,true,true,true,null,null,true,null));
        rejects(command(EntityKind.EPIC,epic,EpicState.ACTIVE,EpicState.COMPLETED,0),LifecycleException.Kind.EVIDENCE_MISSING);
    }

    @Test
    void auditFailureRollsBackStateAndVersion() {
        jdbc.execute("alter table lifecycle_transition add constraint inject_failure check(false)");
        rejects(job(JobState.PENDING,JobState.RUNNING,0),LifecycleException.Kind.STORAGE_UNAVAILABLE);
        assertThat(jdbc.queryForObject("select state from job",String.class)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select state_version from job",Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from lifecycle_transition",Integer.class)).isZero();
    }

    @Test
    void identicalCommandReplaysAfterRestartWithoutAnotherTransition() {
        var command=job(JobState.PENDING,JobState.RUNNING,0);
        var first=service.transition(command);
        restart();
        assertThat(service.transition(command)).isEqualTo(first);
        rejects(new TransitionCommand(command.commandId(),command.entity(),"other-actor",command.expectedState(),command.nextState(),
                command.expectedVersion(),command.approval(),command.execution(),command.attempt()),LifecycleException.Kind.UNAUTHORIZED);
        assertThatThrownBy(()->context.getBean(ChangeLifecycle.class).transition(command)).isInstanceOf(LifecycleException.class)
                .extracting(error->((LifecycleException)error).kind()).isEqualTo(LifecycleException.Kind.UNAUTHORIZED);
        assertThat(jdbc.queryForObject("select count(*) from lifecycle_transition",Integer.class)).isEqualTo(1);
        rejects(new TransitionCommand(command.commandId(),command.entity(),command.actorId(),JobState.RUNNING,JobState.SUCCEEDED,1,approval,execution,attempt),LifecycleException.Kind.COMMAND_CONFLICT);
        assertThat(jdbc.queryForObject("select identity::text from lifecycle_transition",String.class)).contains("plan-v1",approval.approvalId().toString(),execution.processDefinitionId());
    }

    @Test
    void competingCommandsAcceptOneTransitionAndCancellationCannotLoseToStaleCompletion() throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)){
            var pending=new ArrayList<CompletableFuture<Boolean>>();
            for(int i=0;i<2;i++)pending.add(CompletableFuture.supplyAsync(()->{
                try{service.transition(job(JobState.PENDING,JobState.RUNNING,0));return true;}
                catch(LifecycleException stale){assertThat(stale.kind()).isEqualTo(LifecycleException.Kind.STALE_STATE);return false;}
            },pool));
            assertThat(pending.stream().map(CompletableFuture::join).filter(Boolean::booleanValue).count()).isEqualTo(1);
            var results=new ArrayList<CompletableFuture<Boolean>>();
            for(var end:List.of(JobState.CANCELLED,JobState.SUCCEEDED))results.add(CompletableFuture.supplyAsync(()->{
                try{service.transition(job(JobState.RUNNING,end,1));return true;}
                catch(LifecycleException stale){assertThat(stale.kind()).isEqualTo(LifecycleException.Kind.STALE_STATE);return false;}
            },pool));
            assertThat(results.stream().map(CompletableFuture::join).filter(Boolean::booleanValue).count()).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("select count(*) from lifecycle_transition",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select state_version from job",Long.class)).isEqualTo(2);
    }
}
