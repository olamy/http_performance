// Copyright 2017 Yahoo Inc.
// Licensed under the terms of the Apache 2.0 license. Please see LICENSE file in the project root for terms.

package com.yahoo.http.performance;

import com.yahoo.http.performance.request.Request;
import com.yahoo.http.performance.validation.Validation;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A single thread that makes one connection, sends the number of requests (using the requests passed in the constructor)
 * specified in the constructor arg, and validates each event using the validations passed in the constructor.
 */
public class ClientThread implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(ClientThread.class);
    private final CloseableHttpClient httpClient = HttpClients.createDefault();
    private final List<Request> requests;
    private final List<Validation> validations;
    private final long requestDelay;

    private long runTime = 0;
    private long failedRequest = 0;
    private long requestCount;
    private long[] latencies;

    public ClientThread(int requestCount, List<Request> requests, List<Validation> validations, long requestDelay) {
        this.requestCount = requestCount;
        this.requests = requests;
        this.validations = validations;
        this.latencies = new long[(int) requestCount];
        this.requestDelay = requestDelay;
    }

    public void run() {
        long startTime = System.currentTimeMillis();
        List<String> failedValidations = new ArrayList<>();

        for (int i = 0; i < requestCount; i++) {
            try {
                Thread.sleep(requestDelay);
                Request request = requests.get(i % requests.size());
                long start = System.nanoTime();
                CloseableHttpResponse response = request.makeRequest(httpClient);
                long end = System.nanoTime();
                latencies[i] = end - start;

                try {
                    failedValidations.clear();
                    for (Validation v : validations) {
                        if (!v.isValid(response)) {
                            failedValidations.add(v.getName());
                        }
                    }

                    HttpEntity entity = response.getEntity();
                    EntityUtils.consume(entity);

                    if (failedValidations.size() > 0) {
                        throw new IOException("Request failed");
                    }
                } finally {
                    response.close();
                }
            } catch (IOException|InterruptedException e) {
                failedRequest++;
            }
        }
        try {
            httpClient.close();
        } catch (IOException e) {
            LOG.error("Client failed to close.");
        }


        long endTime = System.currentTimeMillis();
        runTime = endTime - startTime;
    }

    public long[] getLatencies() {
        return latencies;
    }

    public long getRequestCount() {
        return requestCount;
    }

    public long getRunTime() {
        return runTime;
    }

    public long getFailedRequest() {
        return failedRequest;
    }

    public long getRequestDelay() {
        return requestDelay;
    }
}
