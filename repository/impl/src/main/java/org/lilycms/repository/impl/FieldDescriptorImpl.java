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
package org.lilycms.repository.impl;

import org.lilycms.repository.api.FieldDescriptor;
import org.lilycms.repository.api.TypeManager;
import org.lilycms.repository.api.ValueType;

public class FieldDescriptorImpl implements FieldDescriptor {

    private final String fieldDescriptorId;
    private Long version;
    private final boolean mandatory;
    private final boolean versionable;
    private final ValueType valueType;

    /**
     * This constructor should not be called directly.
     * @use {@link TypeManager#newFieldDescriptor} instead
     */
    public FieldDescriptorImpl(String fieldDescriptorId, ValueType valueType, boolean mandatory, boolean versionable) {
        this(fieldDescriptorId, null, valueType, mandatory, versionable);
    }

    /**
     * This constructor should not be called directly.
     * @use {@link TypeManager#newFieldDescriptor} instead
     */
    public FieldDescriptorImpl(String fieldDescriptorId, Long version, ValueType valueType, boolean mandatory, boolean versionable) {
        this.fieldDescriptorId = fieldDescriptorId;
        this.version = version;
        this.valueType = valueType;
        this.mandatory = mandatory;
        this.versionable = versionable;
    }

    public ValueType getValueType() {
        return valueType;
    }

    public String getId() {
        return fieldDescriptorId;
    }

    public Long getVersion() {
        return version;
    }
    
    public void setVersion(long version) {
        this.version = version;
    }

    public boolean isMandatory() {
        return mandatory;
    }

    public boolean isVersionable() {
        return versionable;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((fieldDescriptorId == null) ? 0 : fieldDescriptorId.hashCode());
        result = prime * result + ((valueType == null) ? 0 : valueType.hashCode());
        result = prime * result + (mandatory ? 1231 : 1237);
        result = prime * result + ((version == null) ? 0 : version.hashCode());
        result = prime * result + (versionable ? 1231 : 1237);
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
        FieldDescriptorImpl other = (FieldDescriptorImpl) obj;
        if (fieldDescriptorId == null) {
            if (other.fieldDescriptorId != null)
                return false;
        } else if (!fieldDescriptorId.equals(other.fieldDescriptorId))
            return false;
        if (valueType == null) {
            if (other.valueType != null)
                return false;
        } else if (!valueType.equals(other.valueType))
            return false;
        if (mandatory != other.mandatory)
            return false;
        if (version == null) {
            if (other.version != null)
                return false;
        } else if (!version.equals(other.version))
            return false;
        if (versionable != other.versionable)
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "FieldDescriptorImpl [fieldDescriptorId=" + fieldDescriptorId + ", version=" + version + ", valueType="
                        + valueType + ", mandatory=" + mandatory + ", versionable=" + versionable + "]";
    }
    
}