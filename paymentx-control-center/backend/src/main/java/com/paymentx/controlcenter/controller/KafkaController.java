package com.paymentx.controlcenter.controller;

import com.paymentx.controlcenter.client.KafkaAdminMonitoringClient;
import com.paymentx.controlcenter.dto.ApiResponse;
import com.paymentx.controlcenter.dto.kafka.KafkaConsumerGroupSummary;
import com.paymentx.controlcenter.dto.kafka.KafkaPartitionLag;
import com.paymentx.controlcenter.dto.kafka.KafkaTopicInfo;
import com.paymentx.controlcenter.dto.kafka.KafkaTopicThroughput;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * ENGLISH: The dashboard's real, read-only Kafka monitoring API
 * surface. What it does: exposes real topics (with DLT/retry
 * classification), real consumer groups, real per-partition lag for
 * one group, and a real, live-measured throughput sample for one
 * topic - every call delegates straight to KafkaAdminMonitoringClient,
 * which only ever reads from the real broker. There is no endpoint
 * here that creates/deletes a topic or resets/commits an offset.
 *
 * HINGLISH: Dashboard ka real, read-only Kafka monitoring API surface.
 * Ye kya karti hai: real topics (DLT/retry classification ke saath),
 * real consumer groups, ek group ke liye real per-partition lag, aur
 * ek topic ke liye ek real, live-measured throughput sample expose
 * karta hai - har call seedha KafkaAdminMonitoringClient ko delegate
 * karta hai, jo sirf real broker se hamesha padhta hai. Yahan koi
 * endpoint nahi hai jo topic banaye/delete kare ya offset
 * reset/commit kare.
 */
@RestController
@RequestMapping("/api/v1/kafka")
public class KafkaController {

    private final KafkaAdminMonitoringClient client;

    public KafkaController(KafkaAdminMonitoringClient client) {
        this.client = client;
    }

    @GetMapping("/topics")
    public ApiResponse<List<KafkaTopicInfo>> topics() {
        return ApiResponse.success(client.listTopics());
    }

    @GetMapping("/consumer-groups")
    public ApiResponse<List<KafkaConsumerGroupSummary>> consumerGroups() {
        return ApiResponse.success(client.listConsumerGroups());
    }

    @GetMapping("/consumer-groups/{groupId}/lag")
    public ApiResponse<List<KafkaPartitionLag>> consumerGroupLag(@PathVariable String groupId) {
        return ApiResponse.success(client.consumerGroupLag(groupId));
    }

    @GetMapping("/topics/{topicName}/throughput")
    public ApiResponse<KafkaTopicThroughput> throughput(@PathVariable String topicName) {
        return ApiResponse.success(client.topicThroughput(topicName));
    }
}
