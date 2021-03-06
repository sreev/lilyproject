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
package org.lilyproject.linkindex;

import org.lilyproject.repository.api.*;

import java.util.List;
import java.util.Map;

public class RecordLinkExtractor {
    /**
     * Extracts the links from a record. The provided Record object should
     * be "fully loaded" (= contain all fields).
     */
    public static void extract(IdRecord record, LinkCollector collector, Repository repository)
            throws RepositoryException, InterruptedException {
        for (Map.Entry<SchemaId, Object> field : record.getFieldsById().entrySet()) {
            FieldType fieldType;
            try {
                fieldType = repository.getTypeManager().getFieldTypeById(field.getKey());
            } catch (FieldTypeNotFoundException e) {
                // Can not do anything with a field if we cannot load its type
                continue;
            }
            extract(field.getValue(), fieldType, collector, fieldType, record.getId(), repository);
        }
    }

    /**
     * This is for link extraction from nested records.
     */
    private static void extractRecord(Record record, LinkCollector collector, FieldType ctxField, RecordId ctxRecord,
            Repository repository)
            throws RepositoryException, InterruptedException {
        for (Map.Entry<QName, Object> field : record.getFields().entrySet()) {
            FieldType fieldType;
            try {
                fieldType = repository.getTypeManager().getFieldTypeByName(field.getKey());
            } catch (FieldTypeNotFoundException e) {
                // Can not do anything with a field if we cannot load its type
                continue;
            }

            // The ctxField and ctxRecord need to stay the top-level ones! It does not matter how
            // deeply nested a link occurs, as far as the link index is concerned, it still occurs
            // with the field of the top level record.
            extract(field.getValue(), fieldType, collector, ctxField, ctxRecord, repository);
        }
    }

    private static void extract(Object value, FieldType fieldType, LinkCollector collector, FieldType ctxField,
            RecordId ctxRecord, Repository repository) throws RepositoryException, InterruptedException {

        ValueType valueType = fieldType.getValueType();

        String baseType = valueType.getDeepestValueType().getBaseName();

        if (baseType.equals("LINK") || baseType.equals("RECORD")) {
            extract(value, collector, ctxField, ctxRecord, repository);
        }
    }

    private static void extract(Object value, LinkCollector collector, FieldType ctxField, RecordId ctxRecord,
            Repository repository) throws RepositoryException, InterruptedException {

        if (value instanceof List) {
            List list = (List)value;
            for (Object item : list) {
                extract(item, collector, ctxField, ctxRecord, repository);
            }
        } else if (value instanceof Record) {
            extractRecord((Record)value, collector, ctxField, ctxRecord, repository);
        } else if (value instanceof Link) {
            RecordId recordId = ((Link)value).resolve(ctxRecord, repository.getIdGenerator());
            collector.addLink(recordId, ctxField.getId());
        } else {
            throw new RuntimeException("Encountered an unexpected kind of object from a link field: " +
                    value.getClass().getName());
        }
    }
}
