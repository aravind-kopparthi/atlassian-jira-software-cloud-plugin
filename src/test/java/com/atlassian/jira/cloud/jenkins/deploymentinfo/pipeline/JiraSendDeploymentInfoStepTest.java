package com.atlassian.jira.cloud.jenkins.deploymentinfo.pipeline;

import com.atlassian.jira.cloud.jenkins.common.factory.JiraSenderFactory;
import com.atlassian.jira.cloud.jenkins.common.response.JiraSendInfoResponse;
import com.atlassian.jira.cloud.jenkins.config.JiraCloudPluginConfig;
import com.atlassian.jira.cloud.jenkins.config.JiraCloudSiteConfig;
import com.atlassian.jira.cloud.jenkins.deploymentinfo.client.model.DeploymentApiResponse;
import com.atlassian.jira.cloud.jenkins.deploymentinfo.service.JiraDeploymentInfoResponse;
import com.atlassian.jira.cloud.jenkins.deploymentinfo.service.JiraDeploymentInfoSender;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import com.google.common.collect.ImmutableList;
import hudson.model.Job;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.Secret;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.StepConfigTester;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

import javax.inject.Inject;
import java.io.PrintStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.atlassian.jira.cloud.jenkins.common.response.JiraSendInfoResponse.Status.SUCCESS_DEPLOYMENT_ACCEPTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class JiraSendDeploymentInfoStepTest {

    private static final String SITE = "example.atlassian.net";
    private static final String ENVIRONMENT = "prod-east-1";
    private static final String ENVIRONMENT_TYPE = "production";
    private static final String CLIENT_ID = UUID.randomUUID().toString();
    private static final String CREDENTIAL_ID = UUID.randomUUID().toString();

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule public JenkinsRule jenkinsRule = new JenkinsRule();

    @Inject JiraSendDeploymentInfoStep.DescriptorImpl descriptor;

    @Before
    public void setUp() throws Exception {
        jenkinsRule.getInstance().getInjector().injectMembers(this);

        // setup Jira site config
        JiraCloudPluginConfig.get()
                .setSites(
                        ImmutableList.of(new JiraCloudSiteConfig(SITE, CLIENT_ID, CREDENTIAL_ID)));

        // setup credentials
        CredentialsProvider.lookupStores(jenkinsRule.getInstance())
                .iterator()
                .next()
                .addCredentials(Domain.global(), secretCredential());

        // setup JiraDeploymentInfoSender mock
        final JiraSenderFactory mockSenderFactory = mock(JiraSenderFactory.class);
        final JiraDeploymentInfoSender mockSender = mock(JiraDeploymentInfoSender.class);
        when(mockSenderFactory.getJiraDeploymentInfoSender()).thenReturn(mockSender);
        JiraSenderFactory.setInstance(mockSenderFactory);
        final DeploymentApiResponse response =
                new DeploymentApiResponse(
                        Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        final JiraDeploymentInfoResponse deploymentAccepted =
                JiraDeploymentInfoResponse.successDeploymentAccepted(SITE, response);
        when(mockSender.sendDeploymentInfo(any())).thenReturn(deploymentAccepted);
    }

    @Test
    public void configRoundTrip() throws Exception {
        final JiraSendDeploymentInfoStep step =
                new StepConfigTester(jenkinsRule).configRoundTrip(new JiraSendDeploymentInfoStep(SITE, ENVIRONMENT, ENVIRONMENT_TYPE));

        assertThat(step.getSite()).isEqualTo(SITE);
        assertThat(step.getEnvironment()).isEqualTo(ENVIRONMENT);
        assertThat(step.getEnvironmentType()).isEqualTo(ENVIRONMENT_TYPE);
    }

    @Test
    public void testStep() throws Exception {
        // given
        final Run mockRun = mockRun();
        final Job mockJob = mockJob();
        final TaskListener mockTaskListener = mockTaskListener();
        when(mockTaskListener.getLogger()).thenReturn(mock(PrintStream.class));
        when(mockRun.getParent()).thenReturn(mockJob);

        final Map<String, Object> r = new HashMap<>();
        r.put("site", SITE);
        r.put("environment", ENVIRONMENT);
        r.put("environmentType", ENVIRONMENT_TYPE);
        final JiraSendDeploymentInfoStep step = (JiraSendDeploymentInfoStep) descriptor.newInstance(r);

        final StepContext ctx = mock(StepContext.class);
        when(ctx.get(Node.class)).thenReturn(jenkinsRule.getInstance());
        when(ctx.get(Run.class)).thenReturn(mockRun);
        when(ctx.get(TaskListener.class)).thenReturn(mockTaskListener);

        final JiraSendDeploymentInfoStep.JiraSendDeploymentInfoStepExecution start =
                (JiraSendDeploymentInfoStep.JiraSendDeploymentInfoStepExecution) step.start(ctx);

        // when
        final JiraSendInfoResponse response = start.run();

        // then
        assertThat(response.getStatus()).isEqualTo(SUCCESS_DEPLOYMENT_ACCEPTED);
    }

    private static BaseStandardCredentials secretCredential() {
        return new StringCredentialsImpl(
                CredentialsScope.GLOBAL, CREDENTIAL_ID, "test-secret", Secret.fromString("secret"));
    }

    private static Run mockRun() {
        return mock(WorkflowRun.class);
    }

    private static Job mockJob() {
        return mock(Job.class);
    }

    private static TaskListener mockTaskListener() {
        return mock(TaskListener.class);
    }
}
