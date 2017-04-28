/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.processors.cache;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.ignite.internal.util.typedef.F;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 *
 */
public class ExchangeActions {
    /** */
    private Map<String, ActionData> cachesToStart;

    /** */
    private Map<String, ActionData> clientCachesToStart;

    /** */
    private Map<String, ActionData> cachesToStop;

    /** */
    private Map<String, ActionData> cachesToClose;

    /** */
    private Map<String, ActionData> cachesToResetLostParts;

    /** */
    private ClusterState newState;

    public boolean clientOnlyExchange() {
        return F.isEmpty(cachesToStart) &&
            F.isEmpty(cachesToStop) &&
            F.isEmpty(cachesToResetLostParts);
    }

    /**
     * @param nodeId Local node ID.
     * @return Close cache requests.
     */
    public List<DynamicCacheChangeRequest> closeRequests(UUID nodeId) {
        List<DynamicCacheChangeRequest> res = null;

        if (cachesToClose != null) {
            for (ActionData req : cachesToClose.values()) {
                if (nodeId.equals(req.req.initiatingNodeId())) {
                    if (res == null)
                        res = new ArrayList<>(cachesToClose.size());

                    res.add(req.req);
                }
            }
        }

        return res != null ? res : Collections.<DynamicCacheChangeRequest>emptyList();
    }

    /**
     * @return Start cache requests.
     */
    Collection<ActionData> newAndClientCachesStartRequests() {
        if (cachesToStart != null || clientCachesToStart != null) {
            List<ActionData> res = new ArrayList<>();

            if (cachesToStart != null)
                res.addAll(cachesToStart.values());

            if (clientCachesToStart != null)
                res.addAll(clientCachesToStart.values());

            return res;
        }

        return Collections.emptyList();
    }

    /**
     * @return Start cache requests.
     */
    Collection<ActionData> newCachesStartRequests() {
        return cachesToStart != null ? cachesToStart.values() : Collections.<ActionData>emptyList();
    }

    /**
     * @return Stop cache requests.
     */
    public List<DynamicCacheChangeRequest> stopRequests() {
        List<DynamicCacheChangeRequest> res = null;

        if (cachesToStop != null) {
            res = new ArrayList<>(cachesToStop.size());

            for (ActionData req : cachesToStop.values())
                res.add(req.req);
        }

        return res != null ? res : Collections.<DynamicCacheChangeRequest>emptyList();
    }

    /**
     * @param ctx Context.
     */
    public void completeRequestFutures(GridCacheSharedContext ctx) {
        completeRequestFutures(cachesToStart, ctx);
        completeRequestFutures(cachesToStop, ctx);
        completeRequestFutures(cachesToClose, ctx);
        completeRequestFutures(clientCachesToStart, ctx);
        completeRequestFutures(cachesToResetLostParts, ctx);
    }

    private void completeRequestFutures(Map<String, ActionData> map, GridCacheSharedContext ctx) {
        if (map != null) {
            for (ActionData req : map.values())
                ctx.cache().completeCacheStartFuture(req.req, null);
        }
    }

    /**
     * @return {@code True} if have cache stop requests.
     */
    public boolean hasStop() {
        return !F.isEmpty(cachesToStop);
    }

    /**
     * @return
     */
    public Set<String> cachesToResetLostPartitions() {
        Set<String> caches = null;
        
        if (cachesToResetLostParts != null)
            caches = new HashSet<>(cachesToResetLostParts.keySet());

        return caches != null ? caches : Collections.<String>emptySet();
    }
    
    public boolean cacheStopped(int cacheId) {
        if (cachesToStop != null) {
            for (ActionData cache : cachesToStop.values()) {
                if (cache.desc.cacheId() == cacheId)
                    return true;
            }
        }

        return false;
    }

    public boolean cacheStarted(int cacheId) {
        if (cachesToStart != null) {
            for (ActionData cache : cachesToStart.values()) {
                if (cache.desc.cacheId() == cacheId)
                    return true;
            }
        }

        return false;
    }

    /**
     * @param nodeId Local node ID.
     * @return {@code True} if client cache was started.
     */
    public boolean clientCacheStarted(UUID nodeId) {
        if (clientCachesToStart != null) {
            for (ActionData cache : clientCachesToStart.values()) {
                if (nodeId.equals(cache.req.initiatingNodeId()))
                    return true;
            }
        }

        return false;
    }

    /**
     * @return New cluster state if state change was requested.
     */
    @Nullable public ClusterState newClusterState() {
        return newState;
    }

    private Map<String, ActionData> add(Map<String, ActionData> map, DynamicCacheChangeRequest req, DynamicCacheDescriptor desc) {
        assert req != null;
        assert desc != null;

        if (map == null)
            map = new HashMap<>();

        ActionData old = map.put(req.cacheName(), new ActionData(req, desc));

        assert old == null : old;

        return map;
    }

    void addCacheToStart(DynamicCacheChangeRequest req, DynamicCacheDescriptor desc) {
        assert req.start() : req;

        cachesToStart = add(cachesToStart, req, desc);
    }

    void addClientCacheToStart(DynamicCacheChangeRequest req, DynamicCacheDescriptor desc) {
        assert req.start() : req;

        clientCachesToStart = add(clientCachesToStart, req, desc);
    }

    void addCacheToStop(DynamicCacheChangeRequest req, DynamicCacheDescriptor desc) {
        assert req.stop() : req;

        cachesToStop = add(cachesToStop, req, desc);
    }

    void addCacheToClose(DynamicCacheChangeRequest req, DynamicCacheDescriptor desc) {
        assert req.close() : req;

        cachesToClose = add(cachesToClose, req, desc);
    }

    void addCacheToResetLostPartitions(DynamicCacheChangeRequest req, DynamicCacheDescriptor desc) {
        assert req.resetLostPartitions() : req;

        cachesToResetLostParts = add(cachesToResetLostParts, req, desc);
    }

    /**
     * @return {@code True} if there are no cache change actions.
     */
    public boolean empty() {
        return F.isEmpty(cachesToStart) &&
            F.isEmpty(clientCachesToStart) &&
            F.isEmpty(cachesToStop) &&
            F.isEmpty(cachesToClose) &&
            F.isEmpty(cachesToResetLostParts);
    }

    /**
     *
     */
    static class ActionData {
        /** */
        private DynamicCacheChangeRequest req;

        /** */
        private DynamicCacheDescriptor desc;

        ActionData(DynamicCacheChangeRequest req, DynamicCacheDescriptor desc) {
            assert req != null;
            assert desc != null;

            this.req = req;
            this.desc = desc;
        }

        public DynamicCacheChangeRequest request() {
            return req;
        }

        public DynamicCacheDescriptor descriptor() {
            return desc;
        }
    }
}
