/*
 * Copyright 2010 Outerthought bvba
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lilyproject.rowlog.api;

import org.lilyproject.util.ObjectUtils;

/**
 * A value object describing a subscription.
 */
public class RowLogSubscription implements Comparable<RowLogSubscription> {
    private final String rowLogId;
    private final String id;
    private Type type;
    private int orderNr;

    /**
     * The type of a subscription defines if the listeners of a subscription run locally (VM) or remote (Netty) 
     * The WAL type is a special type that should only be used for the {@link WalProcessor}
     */
    public enum Type {VM, Netty, WAL}
    
    /**
     * Constructor
     * @param rowLogId id of the rowlog to which the subscription belongs
     * @param id of the subscription
     * @param type 
     * @param orderNr a number defining the subscription's position between the other subscriptions of the rowlog
     */
    public RowLogSubscription(String rowLogId, String id, Type type,int orderNr) {
        this.rowLogId = rowLogId;
        this.id = id;
        this.type = type;
        this.orderNr = orderNr;
    }

    public String getRowLogId() {
        return rowLogId;
    }

    public String getId() {
        return id;
    }
    
    public Type getType() {
        return type;
    }
    
    public int getOrderNr() {
        return orderNr;
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((rowLogId == null) ? 0 : rowLogId.hashCode());
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        result = prime * result + orderNr;
        result = prime * result + ((type == null) ? 0 : type.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        RowLogSubscription other = (RowLogSubscription) obj;
        if (!ObjectUtils.safeEquals(rowLogId, other.rowLogId))
            return false;
        if (!ObjectUtils.safeEquals(id, other.id))
            return false;
        if (orderNr != other.orderNr)
            return false;
        if (!ObjectUtils.safeEquals(type, other.type))
            return false;
        return true;
    }

    @Override
    public int compareTo(RowLogSubscription other) {
        return orderNr - other.orderNr;
    }
}
