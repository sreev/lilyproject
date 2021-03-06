/*
 * Copyright 2012 NGDATA nv
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
package org.lilyproject.indexer.integration;

import org.lilyproject.indexer.model.util.IndexInfo;
import org.lilyproject.indexer.model.util.IndexesInfo;
import org.lilyproject.plugin.PluginRegistry;
import org.lilyproject.repository.api.*;
import org.lilyproject.repository.spi.RecordUpdateHook;
import org.lilyproject.util.repo.RecordEvent;

import javax.annotation.PreDestroy;
import java.util.Collection;
import java.util.Set;

/**
 * A record update hook that adds information to the RecordEvent needed to evaluate the
 * IndexRecordFilter, especially needed to be able to know what IndexRecordFilter's matched
 * on the previous (or deleted) record state. Also allows to make this decision without
 * needing to read the complete record.
 */
public class IndexRecordFilterHook implements RecordUpdateHook {
    private PluginRegistry pluginRegistry;
    private IndexesInfo indexesInfo;

    /**
     * Name should be unique among all RecordUpdateHook's. Because the name starts
     * with 'org.lilyproject', this hook does not need to be activated through
     * configuration.
     */
    private String NAME = "org.lilyproject.IndexRecordFilterHook";

    public IndexRecordFilterHook(IndexesInfo indexesInfo) {
        this.indexesInfo = indexesInfo;
    }

    public IndexRecordFilterHook(PluginRegistry pluginRegistry, IndexesInfo indexesInfo) {
        this.pluginRegistry = pluginRegistry;
        this.indexesInfo = indexesInfo;

        pluginRegistry.addPlugin(RecordUpdateHook.class, NAME, this);
    }

    @PreDestroy
    public void destroy() {
        // Use same arguments as for addPlugin
        pluginRegistry.removePlugin(RecordUpdateHook.class, NAME, this);
    }

    @Override
    public void beforeUpdate(Record record, Record originalRecord, Repository repository, FieldTypes fieldTypes,
            RecordEvent recordEvent) throws RepositoryException, InterruptedException {
        Collection<IndexInfo> indexInfos = indexesInfo.getIndexInfos();
        if (indexInfos.size() > 0) {
            TypeManager typeMgr = repository.getTypeManager();

            RecordEvent.IndexRecordFilterData idxSel = new RecordEvent.IndexRecordFilterData();
            recordEvent.setIndexRecordFilterData(idxSel);

            idxSel.setOldRecordExists(true);
            idxSel.setNewRecordExists(true);

            if (indexesInfo.getRecordFilterDependsOnRecordType()) {
                // Because record type names can change, this is not guaranteed to be the same as what gets
                // stored in the repo, but that doesn't matter as it is only for indexing purposes.
                SchemaId oldRecordTypeId = typeMgr.getRecordTypeByName(originalRecord.getRecordTypeName(), null).getId();
                idxSel.setOldRecordType(oldRecordTypeId);
                // on update, specifying record type is optional
                idxSel.setNewRecordType(record.getRecordTypeName() != null ?
                        typeMgr.getRecordTypeByName(record.getRecordTypeName(), null).getId() : oldRecordTypeId);
            }

            Set<QName> names = indexesInfo.getRecordFilterFieldDependencies();
            for (QName name : names) {
                Object oldValue = null, newValue = null;
                if (record.hasField(name)) {
                    newValue = record.getField(name);
                }
                if (originalRecord.hasField(name)) {
                    oldValue = originalRecord.getField(name);
                }
                if (oldValue != null || newValue != null) {
                    FieldType type = fieldTypes.getFieldType(name);
                    addField(type, oldValue, newValue, idxSel);
                }
            }
        }
    }

    @Override
    public void beforeCreate(Record newRecord, Repository repository, FieldTypes fieldTypes, RecordEvent recordEvent)
            throws RepositoryException, InterruptedException {

        Collection<IndexInfo> indexInfos = indexesInfo.getIndexInfos();
        if (indexInfos.size() > 0) {
            TypeManager typeMgr = repository.getTypeManager();

            RecordEvent.IndexRecordFilterData idxSel = new RecordEvent.IndexRecordFilterData();
            recordEvent.setIndexRecordFilterData(idxSel);

            idxSel.setOldRecordExists(false);
            idxSel.setNewRecordExists(true);

            if (indexesInfo.getRecordFilterDependsOnRecordType()) {
                idxSel.setNewRecordType(typeMgr.getRecordTypeByName(newRecord.getRecordTypeName(), null).getId());
            }

            Set<QName> names = indexesInfo.getRecordFilterFieldDependencies();
            for (QName name : names) {
                if (newRecord.hasField(name)) {
                    Object newValue = newRecord.getField(name);
                    FieldType type = fieldTypes.getFieldType(name);
                    addField(type, null, newValue, idxSel);
                }
            }
        }
    }

    @Override
    public void beforeDelete(Record originalRecord, Repository repository, FieldTypes fieldTypes,
            RecordEvent recordEvent) throws RepositoryException, InterruptedException {
        Collection<IndexInfo> indexInfos = indexesInfo.getIndexInfos();
        if (indexInfos.size() > 0) {
            TypeManager typeMgr = repository.getTypeManager();

            RecordEvent.IndexRecordFilterData idxSel = new RecordEvent.IndexRecordFilterData();
            recordEvent.setIndexRecordFilterData(idxSel);

            idxSel.setOldRecordExists(true);
            idxSel.setNewRecordExists(false);

            if (indexesInfo.getRecordFilterDependsOnRecordType()) {
                idxSel.setOldRecordType(typeMgr.getRecordTypeByName(originalRecord.getRecordTypeName(), null).getId());
            }

            Set<QName> names = indexesInfo.getRecordFilterFieldDependencies();
            for (QName name : names) {
                if (originalRecord.hasField(name)) {
                    Object oldValue = originalRecord.getField(name);
                    FieldType type = fieldTypes.getFieldType(name);
                    addField(type, oldValue, null, idxSel);
                }
            }
        }
    }

    private void addField(FieldType type, Object oldValue, Object newValue, RecordEvent.IndexRecordFilterData idxSel)
            throws RepositoryException, InterruptedException {

        if (oldValue == null && newValue == null) {
            return;
        }

        if (oldValue != null) {
            oldValue = type.getValueType().toBytes(oldValue, new IdentityRecordStack());
        }

        if (newValue != null) {
            newValue = type.getValueType().toBytes(newValue, new IdentityRecordStack());
        }

        idxSel.addChangedField(type.getId(), (byte[])oldValue, (byte[])newValue);
    }
}
