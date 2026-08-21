package com.paymentx.controlcenter.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.paymentx.controlcenter.config.ControlCenterProperties;
import com.paymentx.controlcenter.dto.rabbitmq.*;
import com.paymentx.controlcenter.exception.ControlCenterException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * ENGLISH: The real HTTP client for RabbitMQ's Management HTTP API,
 * authenticated via the same guest/guest local-dev credentials every
 * PaymentX developer already uses (see infra/docker-compose.yml),
 * attached once as a BasicAuthenticationInterceptor on the
 * rabbitMqRestTemplate bean rather than per-call here. What it does:
 * real GETs against /api/overview, /api/connections, /api/channels,
 * /api/queues, and /api/bindings - never a PUT/POST/DELETE against
 * the management API, so this client cannot purge a queue, close a
 * connection, or delete a binding. Why it exists: the actual Phase 2
 * "real RabbitMQ Management API integration" requirement.
 *
 * HINGLISH: RabbitMQ ke Management HTTP API ke liye real HTTP client,
 * usi guest/guest local-dev credentials se authenticated jo har
 * PaymentX developer already use karta hai (infra/docker-compose.yml
 * dekho), ek baar rabbitMqRestTemplate bean par
 * BasicAuthenticationInterceptor ke roop me attach kiya gaya, yahan
 * har-call par nahi. Ye kya karti hai: /api/overview, /api/
 * connections, /api/channels, /api/queues, aur /api/bindings ke
 * against real GETs - management API ke against kabhi
 * PUT/POST/DELETE nahi, isliye ye client kisi queue ko purge, kisi
 * connection ko close, ya kisi binding ko delete nahi kar sakta. Ye
 * dashboard me kyu hai: yehi actual Phase 2 "real RabbitMQ Management
 * API integration" requirement hai.
 */
@Component
public class RabbitMqManagementClient {

    private final RestTemplate restTemplate;
    private final ControlCenterProperties properties;

    public RabbitMqManagementClient(RestTemplate rabbitMqRestTemplate, ControlCenterProperties properties) {
        this.restTemplate = rabbitMqRestTemplate;
        this.properties = properties;
    }

    public RabbitMqOverview overview() {
        JsonNode body = get("/api/overview");
        JsonNode stats = body.path("object_totals");
        return new RabbitMqOverview(
                body.path("management_version").asText(null),
                body.path("rabbitmq_version").asText(body.path("product_version").asText(null)),
                body.path("cluster_name").asText(null),
                stats.path("connections").isMissingNode() ? null : stats.path("connections").asLong(),
                stats.path("channels").isMissingNode() ? null : stats.path("channels").asLong(),
                stats.path("queues").isMissingNode() ? null : stats.path("queues").asLong(),
                body.path("queue_totals").path("messages").isMissingNode() ? null : body.path("queue_totals").path("messages").asLong());
    }

    public List<RabbitMqConnectionSummary> connections() {
        JsonNode body = get("/api/connections");
        List<RabbitMqConnectionSummary> result = new ArrayList<>();
        for (JsonNode c : body) {
            result.add(new RabbitMqConnectionSummary(
                    c.path("name").asText(null),
                    c.path("state").asText(null),
                    c.path("host").asText(null),
                    c.path("port").isMissingNode() ? null : c.path("port").asInt(),
                    c.path("peer_host").asText(null),
                    c.path("peer_port").isMissingNode() ? null : c.path("peer_port").asInt(),
                    c.path("protocol").asText(null),
                    c.path("user").asText(null),
                    c.path("connected_at").isMissingNode() ? null : c.path("connected_at").asLong()));
        }
        return result;
    }

    public List<RabbitMqChannelSummary> channels() {
        JsonNode body = get("/api/channels");
        List<RabbitMqChannelSummary> result = new ArrayList<>();
        for (JsonNode c : body) {
            result.add(new RabbitMqChannelSummary(
                    c.path("name").asText(null),
                    c.path("connection_details").path("name").asText(null),
                    c.path("state").asText(null),
                    c.path("consumer_count").isMissingNode() ? null : c.path("consumer_count").asInt(),
                    c.path("messages_unacknowledged").isMissingNode() ? null : c.path("messages_unacknowledged").asLong()));
        }
        return result;
    }

    public List<RabbitMqQueueSummary> queues() {
        JsonNode body = get("/api/queues");
        List<RabbitMqQueueSummary> result = new ArrayList<>();
        for (JsonNode q : body) {
            String name = q.path("name").asText("");
            String lowerName = name.toLowerCase();
            result.add(new RabbitMqQueueSummary(
                    name,
                    q.path("vhost").asText(null),
                    q.path("state").asText(null),
                    q.path("durable").asBoolean(false),
                    q.path("consumers").asInt(0),
                    q.path("messages_ready").asLong(0),
                    q.path("messages_unacknowledged").asLong(0),
                    q.path("messages").asLong(0),
                    q.path("message_stats").path("publish_details").path("rate").asDouble(0.0),
                    q.path("message_stats").path("deliver_details").path("rate").asDouble(0.0),
                    lowerName.contains("dlq") || lowerName.contains("dead-letter") || lowerName.contains("dead_letter"),
                    lowerName.contains("retry")));
        }
        return result;
    }

    public List<RabbitMqBindingSummary> bindings() {
        JsonNode body = get("/api/bindings");
        List<RabbitMqBindingSummary> result = new ArrayList<>();
        for (JsonNode b : body) {
            result.add(new RabbitMqBindingSummary(
                    b.path("source").asText(null),
                    b.path("destination").asText(null),
                    b.path("destination_type").asText(null),
                    b.path("routing_key").asText(null),
                    b.path("vhost").asText(null)));
        }
        return result;
    }

    private JsonNode get(String path) {
        String url = properties.getRabbitmq().getManagementUrl() + path;
        try {
            JsonNode body = restTemplate.getForObject(url, JsonNode.class);
            if (body == null) {
                throw new ControlCenterException("RABBITMQ_EMPTY_RESPONSE", "Empty response from RabbitMQ management API at " + path);
            }
            return body;
        } catch (ResourceAccessException connectionFailure) {
            throw new ControlCenterException("RABBITMQ_UNREACHABLE",
                    "Could not reach RabbitMQ management API: " + connectionFailure.getMostSpecificCause().getMessage(), connectionFailure);
        } catch (RestClientException httpError) {
            throw new ControlCenterException("RABBITMQ_ERROR", "RabbitMQ management API call failed: " + httpError.getMessage(), httpError);
        }
    }
}
