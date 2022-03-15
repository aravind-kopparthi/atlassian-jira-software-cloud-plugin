package com.atlassian.jira.cloud.jenkins.config;

import com.google.common.collect.ImmutableList;
import hudson.model.Descriptor;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.stapler.RequestImpl;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.WebApp;
import org.mockito.Mockito;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.mock;

public class JiraCloudPluginConfigTest {

    private static final String SITE = "example" + Math.random() + ".atlassian.net";
    private static final String CLIENT_ID = "clientId";
    private static final String CREDENTIALS_ID = "credsId";

    private static final JSONObject SITES_JSON =
            (JSONObject)
                    JSONSerializer.toJSON(
                            "{\n"
                                    + "    \"sites\": [\n"
                                    + "        {\n"
                                    + "            \"site\": \"mysite1.atlassian.net\",\n"
                                    + "            \"clientId\": \"myClientId1\",\n"
                                    + "            \"credentialsId\": \"myCreds1\"\n"
                                    + "        },\n"
                                    + "        {\n"
                                    + "            \"site\": \"mysite2.atlassian.net\",\n"
                                    + "            \"clientId\": \"\", "
                                    + "            \"credentialsId\": \"myCreds2\"\n"
                                    + "        }\n"
                                    + "    ]\n"
                                    + "}");

    private static final JSONObject SINGLE_SITE_JSON =
            (JSONObject)
                    JSONSerializer.toJSON(
                            "{\n"
                                    + "    \"sites\": \n"
                                    + "        {\n"
                                    + "            \"site\": \"mysite1.atlassian.net\",\n"
                                    + "            \"clientId\": \"myClientId1\",\n"
                                    + "            \"credentialsId\": \"myCreds1\"\n"
                                    + "        }"
                                    + "}");

    private static final JSONObject AUTO_BUILDS_JSON =
            (JSONObject)
                    JSONSerializer.toJSON(
                            "{\n"
                                    + "    \"autoBuilds\": { "
                                    + "           \"autoBuildsRegex\":\"blah\""
                                    + "    } "
                                    + "}");

    private static final JSONObject AUTO_DEPLOYMENTS_JSON =
            (JSONObject)
                    JSONSerializer.toJSON(
                            "{\n"
                                    + "    \"autoDeployments\": { "
                                    + "           \"autoDeploymentsRegex\":\"blah\""
                                    + "    } "
                                    + "}");

    private static final JSONObject AUTO_DEPLOYMENTS_JSON_WITHOUT_REGEX =
            (JSONObject)
                    JSONSerializer.toJSON(
                            "{\n"
                                    + "    \"autoDeployments\": { "
                                    + "           \"autoDeploymentsRegex\":\"\""
                                    + "    } "
                                    + "}");

    @Rule
    public JenkinsRule jRule = new JenkinsRule();

    @Test
    public void testDoesNotGetSiteConfig_whenSiteIsNotConfigured() {
        // when
        final Optional<JiraCloudSiteConfig2> config =
                JiraCloudPluginConfig.getJiraCloudSiteConfig2(SITE);

        // then
        assertThat(config.isPresent()).isFalse();
    }

    @Test
    public void testGetSiteConfig_whenSiteIsConfigured() {
        // given
        JiraCloudSiteConfig2 siteConfig = new JiraCloudSiteConfig2(SITE, CLIENT_ID, CREDENTIALS_ID);
        JiraCloudPluginConfig.get().setSites2(ImmutableList.of(siteConfig));

        // when
        final Optional<JiraCloudSiteConfig2> config =
                JiraCloudPluginConfig.getJiraCloudSiteConfig2(SITE);

        // then
        assertSiteConfig(config.get(), SITE, CREDENTIALS_ID);
    }

    @Test
    public void testGetSiteConfig_whenSiteIsNotProvided() {
        // given
        JiraCloudSiteConfig2 siteConfig = new JiraCloudSiteConfig2(SITE, CLIENT_ID, CREDENTIALS_ID);
        JiraCloudPluginConfig.get().setSites2(ImmutableList.of(siteConfig));

        // when
        final Optional<JiraCloudSiteConfig2> config =
                JiraCloudPluginConfig.getJiraCloudSiteConfig2(null);

        // then
        assertSiteConfig(config.get(), SITE, CREDENTIALS_ID);
    }

    @Test
    public void testGetSiteConfig_whenMultipleSitesConfigured() {
        // given
        JiraCloudSiteConfig2 siteConfig1 = new JiraCloudSiteConfig2(SITE, CLIENT_ID, CREDENTIALS_ID);
        JiraCloudSiteConfig2 siteConfig2 =
                new JiraCloudSiteConfig2("foobar.atlassian.net", "clientId", "credsId");
        JiraCloudPluginConfig.get().setSites2(ImmutableList.of(siteConfig1, siteConfig2));

        // when
        // when
        final Optional<JiraCloudSiteConfig2> config =
                JiraCloudPluginConfig.getJiraCloudSiteConfig2(SITE);

        // then
        assertSiteConfig(config.get(), SITE, CREDENTIALS_ID);
    }

    @Test
    public void testConfigure_populateSites() throws Descriptor.FormException {
        final String configName = "config" + Math.random();

        new JiraCloudPluginConfig(configName).configure(mockStapler(), SITES_JSON);
        final JiraCloudPluginConfig loadedConfig = new JiraCloudPluginConfig(configName);

        assertThat(loadedConfig.getSites2()).hasSize(2);
        assertThat(loadedConfig.getSites2().get(0))
                .isEqualTo(
                        new JiraCloudSiteConfig2(
                                "mysite1.atlassian.net", "myClientId1", "myCreds1"));
    }

    @Test
    public void testConfigure_populateSingleSite() throws Descriptor.FormException {
        final String configName = "config" + Math.random();

        // This simulates the case when you enter a single site via the Jenkins config page.
        // Jenkins apparently sends a single JSON object instead of a JSONArray with size 1
        new JiraCloudPluginConfig(configName).configure(mockStapler(), SINGLE_SITE_JSON);
        final JiraCloudPluginConfig loadedConfig = new JiraCloudPluginConfig(configName);

        assertThat(loadedConfig.getSites2()).hasSize(1);
        assertThat(loadedConfig.getSites2().get(0))
                .isEqualTo(
                        new JiraCloudSiteConfig2(
                                "mysite1.atlassian.net", "myClientId1", "myCreds1"));
    }

    @Test
    public void testConfigure_dropsSites() throws Descriptor.FormException {
        final String configName = "config" + Math.random();

        new JiraCloudPluginConfig(configName).configure(mockStapler(), SITES_JSON);
        new JiraCloudPluginConfig(configName).configure(mockStapler(), new JSONObject());

        final JiraCloudPluginConfig loadedConfig = new JiraCloudPluginConfig(configName);
        assertThat(loadedConfig.getSites2()).hasSize(0);
    }

    @Test
    public void testConfigure_populatesAutoBuildsEnabled() throws Descriptor.FormException {
        final String configName = "config" + Math.random();

        new JiraCloudPluginConfig(configName).configure(mockStapler(), AUTO_BUILDS_JSON);

        final JiraCloudPluginConfig loadedConfig = new JiraCloudPluginConfig(configName);
        assertThat(loadedConfig.getAutoBuildsEnabled()).isTrue();
        assertThat(loadedConfig.getAutoBuildsRegex()).isEqualTo("blah");
    }

    @Test
    public void testConfigure_populatesAutoDeploymentsEnabled() {
        final String configName = "config" + Math.random();

        try {
            new JiraCloudPluginConfig(configName)
                    .configure(mockStapler(), AUTO_DEPLOYMENTS_JSON_WITHOUT_REGEX);
            fail("expecting FormException because Deployments regex must be provided!");
        } catch (Descriptor.FormException e) {
            assertThat(e.getMessage()).contains("Deployments RegEx must be provided");
        }
    }

    @Test
    public void testConfigure_dropsAutoBuildsEnabled_butPreservesRegex()
            throws Descriptor.FormException {
        final String configName = "config" + Math.random();

        new JiraCloudPluginConfig(configName).configure(mockStapler(), AUTO_BUILDS_JSON);
        new JiraCloudPluginConfig(configName).configure(mockStapler(), new JSONObject());

        final JiraCloudPluginConfig loadedConfig = new JiraCloudPluginConfig(configName);
        assertThat(loadedConfig.getAutoBuildsEnabled()).isFalse();
        assertThat(loadedConfig.getAutoBuildsRegex()).isEqualTo("blah");
    }

    @Test
    public void testConfigure_dropsAutoDeploymentsEnabled_butPreservesRegex()
            throws Descriptor.FormException {
        final String configName = "config" + Math.random();

        new JiraCloudPluginConfig(configName).configure(mockStapler(), AUTO_DEPLOYMENTS_JSON);
        new JiraCloudPluginConfig(configName).configure(mockStapler(), new JSONObject());

        final JiraCloudPluginConfig loadedConfig = new JiraCloudPluginConfig(configName);
        assertThat(loadedConfig.getAutoDeploymentsEnabled()).isFalse();
        assertThat(loadedConfig.getAutoDeploymentsRegex()).isEqualTo("blah");
    }

    private static StaplerRequest mockStapler() {
        final Stapler stapler = mock(Stapler.class);
        final WebApp webApp = new WebApp(mock(ServletContext.class));
        Mockito.when(stapler.getWebApp()).thenReturn(webApp);
        return new RequestImpl(
                stapler, mock(HttpServletRequest.class), Collections.emptyList(), null);
    }

    private void assertSiteConfig(
            final JiraCloudSiteConfig2 config,
            final String expectedSite,
            final String expectedCredentialsId) {
        assertThat(config.getSite()).isEqualTo(expectedSite);
        assertThat(config.getCredentialsId()).isEqualTo(expectedCredentialsId);
    }
}
