package com.atlassian.jira.cloud.jenkins.common.client;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.io.NotSerializableException;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;

public abstract class JenkinsAppApi<ResponseEntity> {

    private static final Logger log = LoggerFactory.getLogger(JenkinsAppApi.class);
    private static final MediaType JSON_CONTENT_TYPE =
            MediaType.get("application/json; charset=utf-8");
    private static final MediaType JWT_CONTENT_TYPE = MediaType.get("application/jwt");

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Inject
    public JenkinsAppApi(final OkHttpClient httpClient, final ObjectMapper objectMapper) {
        this.httpClient = Objects.requireNonNull(httpClient);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    protected ResponseEntity sendRequest(
            final String webhookUrl,
            final JenkinsAppRequest jenkinsAppRequest,
            final Class<ResponseEntity> responseClass)
            throws ApiUpdateFailedException {
        try {
            final String requestPayload = objectMapper.writeValueAsString(jenkinsAppRequest);
            RequestBody body = RequestBody.create(JSON_CONTENT_TYPE, requestPayload);
            Request request = new Request.Builder().url(webhookUrl).post(body).build();
            final Response response = httpClient.newCall(request).execute();
            checkForErrorResponse(response);
            return handleResponseBody(response, responseClass);
        } catch (Exception e) {
            throw handleError(e);
        }
    }

    protected ResponseEntity sendRequestAsJwt(
            final String webhookUrl,
            final String secret,
            final JenkinsAppRequest jenkinsAppRequest,
            final Class<ResponseEntity> responseClass)
            throws ApiUpdateFailedException {
        try {
            final String requestPayload = wrapInJwt(jenkinsAppRequest, secret);
            RequestBody body = RequestBody.create(JWT_CONTENT_TYPE, requestPayload);
            Request request = new Request.Builder().url(webhookUrl).post(body).build();
            final Response response = httpClient.newCall(request).execute();
            checkForErrorResponse(response);
            return handleResponseBody(response, responseClass);
        } catch (Exception e) {
            throw handleError(e);
        }
    }

    private ApiUpdateFailedException handleError(final Exception e) {
        if (e instanceof NotSerializableException) {
            return new ApiUpdateFailedException(
                    String.format("Invalid JSON payload: %s", e.getMessage()), e);
        } else if (e instanceof JsonProcessingException) {
            return new ApiUpdateFailedException(
                    String.format("Unable to create the request payload: %s", e.getMessage()), e);
        } else if (e instanceof IOException) {
            return new ApiUpdateFailedException(
                    String.format(
                            "Server exception when submitting update to Jenkins app in Jira: %s",
                            e.getMessage()),
                    e);
        } else if (e instanceof RequestNotPermitted) {
            return new ApiUpdateFailedException("Rate limit reached " + e.getMessage(), e);
        } else {
            return new ApiUpdateFailedException(
                    String.format(
                            "Unexpected error when submitting update to Jira: %s", e.getMessage()),
                    e);
        }
    }

    private void checkForErrorResponse(final Response response) throws IOException {
        if (!response.isSuccessful()) {
            final String message =
                    String.format(
                            "Error response code %d when submitting update to Jenkins app in Jira",
                            response.code());
            final ResponseBody responseBody = response.body();
            if (responseBody != null) {
                log.error(
                        String.format(
                                "Error response body when submitting update to Jenkins app in Jira: %s",
                                responseBody.string()));
                responseBody.close();
            }

            throw new ApiUpdateFailedException(message);
        }
    }

    private ResponseEntity handleResponseBody(
            final Response response, final Class<ResponseEntity> responseClass) throws IOException {
        ResponseBody body = response.body();
        if (body == null) {
            final String message =
                    "Empty response body when submitting update to Jenkins app in Jira";

            throw new ApiUpdateFailedException(message);
        }

        return objectMapper.readValue(
                body.bytes(), objectMapper.getTypeFactory().constructType(responseClass));
    }

    @VisibleForTesting
    protected String wrapInJwt(final JenkinsAppRequest request, final String secret)
            throws JsonProcessingException {
        final String body = objectMapper.writeValueAsString(request);
        Algorithm algorithm = Algorithm.HMAC256(secret);
        return JWT.create()
                .withIssuer("jenkins-plugin")
                .withAudience("jenkins-forge-app")
                .withIssuedAt(new Date())
                .withExpiresAt(Date.from(Instant.now().plusSeconds(5 * 60)))
                .withClaim("request_body_json", body)
                .sign(algorithm);
    }
}
