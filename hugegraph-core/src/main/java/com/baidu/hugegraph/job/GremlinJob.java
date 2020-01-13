/*
 * Copyright 2017 HugeGraph Authors
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.baidu.hugegraph.job;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.baidu.hugegraph.backend.query.Query;
import com.baidu.hugegraph.exception.LimitExceedException;
import com.baidu.hugegraph.traversal.optimize.HugeScriptTraversal;
import com.baidu.hugegraph.util.JsonUtil;

public class GremlinJob extends Job<Object> {

    public static final String TASK_TYPE = "gremlin";
    public static final String TASK_BIND_NAME = "gremlinJob";
    public static final int TASK_RESULTS_MAX_SIZE = 10000;

    @Override
    public String type() {
        return TASK_TYPE;
    }

    @Override
    public Object execute() throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> input = JsonUtil.fromJson(this.task().input(),
                                                      Map.class);
        String gremlin = (String) input.get("gremlin");
        @SuppressWarnings("unchecked")
        Map<String, Object> bindings = (Map<String, Object>)
                                       input.get("bindings");
        String language = (String) input.get("language");
        @SuppressWarnings("unchecked")
        Map<String, String> aliases = (Map<String, String>)
                                      input.get("aliases");

        bindings.put(TASK_BIND_NAME, new GremlinJobProxy());

        HugeScriptTraversal<?, ?> traversal = new HugeScriptTraversal<>(
                                                  this.graph().traversal(),
                                                  language, gremlin,
                                                  bindings, aliases);
        List<Object> results = new ArrayList<>();
        long capacity = Query.defaultCapacity(Query.NO_CAPACITY);
        try {
            while (traversal.hasNext()) {
                Object result = traversal.next();
                results.add(result);
                checkResultsSize(results);
                Thread.yield();
            }
        } finally {
            Query.defaultCapacity(capacity);
            traversal.close();
            this.graph().tx().commit();
        }

        Object result = traversal.result();
        if (result != null) {
            checkResultsSize(result);
            return result;
        } else {
            return results;
        }
    }

    private void checkResultsSize(Object results) {
        int size = 0;
        if (results instanceof Collection) {
            size = ((Collection<?>) results).size();
        }
        if (size > TASK_RESULTS_MAX_SIZE) {
            throw new LimitExceedException(
                      "Job results size %s has exceeded the max limit %s",
                      size, TASK_RESULTS_MAX_SIZE);
        }
    }

    /**
     * Used by gremlin script
     */
    @SuppressWarnings("unused")
    private class GremlinJobProxy {

        public void setMinSaveInterval(long seconds) {
            GremlinJob.this.setMinSaveInterval(seconds);
        }

        public void updateProgress(int progress) {
            GremlinJob.this.updateProgress(progress);
        }

        public int progress() {
            return GremlinJob.this.progress();
        }
    }
}