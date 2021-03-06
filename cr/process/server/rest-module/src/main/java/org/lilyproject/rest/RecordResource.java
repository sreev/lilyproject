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
package org.lilyproject.rest;

import org.lilyproject.repository.api.*;
import org.lilyproject.tools.import_.core.ImportMode;
import org.lilyproject.tools.import_.core.ImportResult;
import org.lilyproject.tools.import_.core.ImportResultType;
import org.lilyproject.tools.import_.core.RecordImport;

import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.*;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import java.net.URI;
import java.util.List;

import static javax.ws.rs.core.Response.Status.*;

@Path("record/{id}")
public class RecordResource extends RepositoryEnabled {

    @GET
    @Produces("application/json")
    public Entity<Record> get(@PathParam("id") String id, @Context UriInfo uriInfo) {
        RecordId recordId = repository.getIdGenerator().fromString(id);
        List<QName> fieldQNames = ResourceClassUtil.parseFieldList(uriInfo);
        try {
            return Entity.create(repository.read(recordId, fieldQNames), uriInfo);
        } catch (RecordNotFoundException e) {
            throw new ResourceException(e, NOT_FOUND.getStatusCode());
        } catch (Exception e) {
            throw new ResourceException("Error loading record.", e, INTERNAL_SERVER_ERROR.getStatusCode());
        }
    }

    @PUT
    @Produces("application/json")
    @Consumes("application/json")
    public Response put(@PathParam("id") String id, Record record, @Context UriInfo uriInfo) {
        RecordId recordId = repository.getIdGenerator().fromString(id);

        if (record.getId() != null && !record.getId().equals(recordId)) {
            throw new ResourceException("Record id in submitted record does not match record id in URI.",
                    BAD_REQUEST.getStatusCode());
        }

        record.setId(recordId);

        ImportResult<Record> result;
        try {
            result = RecordImport.importRecord(record, ImportMode.CREATE_OR_UPDATE, repository);
        } catch (Exception e) {
            throw new ResourceException("Error creating or updating record with id " + id, e,
                    INTERNAL_SERVER_ERROR.getStatusCode());
        }

        // TODO record we respond with should be full record or be limited to user-specified field list
        record = result.getEntity();
        Response response;

        ImportResultType resultType = result.getResultType();
        switch (resultType) {
            case CREATED:
                URI uri = UriBuilder.fromResource(RecordResource.class).build(record.getId());
                response = Response.created(uri).entity(Entity.create(record, uriInfo)).build();
                break;
            case UPDATED:
            case UP_TO_DATE:
                response = Response.ok(Entity.create(record, uriInfo)).build();
                break;
            default:
                throw new RuntimeException("Unexpected import result type: " + resultType);
        }

        return response;
    }

    @POST
    @Produces("application/json")
    @Consumes("application/json")
    public Response post(@PathParam("id") String id, PostAction<Record> postAction, @Context UriInfo uriInfo) {
        if (postAction.getAction().equals("update")) {
            RecordId recordId = repository.getIdGenerator().fromString(id);
            Record record = postAction.getEntity();

            if (record.getId() != null && !record.getId().equals(recordId)) {
                throw new ResourceException("Record id in submitted record does not match record id in URI.",
                        BAD_REQUEST.getStatusCode());
            }

            record.setId(recordId);

            ImportResult<Record> result;
            try {
                result = RecordImport.importRecord(record, ImportMode.UPDATE, postAction.getConditions(), repository);
            } catch (Exception e) {
                throw new ResourceException(e, INTERNAL_SERVER_ERROR.getStatusCode());
            }

            // TODO record we respond with should be full record or be limited to user-specified field list
            record = result.getEntity();
            Response response;

            ImportResultType resultType = result.getResultType();
            switch (resultType) {
                case CANNOT_UPDATE_DOES_NOT_EXIST:
                    throw new ResourceException("Record not found: " + recordId, NOT_FOUND.getStatusCode());
                case UPDATED:
                case UP_TO_DATE:
                    response = Response.ok(Entity.create(record, uriInfo)).build();
                    break;
                case CONDITION_CONFLICT:
                    response = Response.status(CONFLICT.getStatusCode()).entity(Entity.create(record, uriInfo)).build();
                    break;
                default:
                    throw new RuntimeException("Unexpected import result type: " + resultType);
            }

            return response;

        } else if (postAction.getAction().equals("delete")) {
            RecordId recordId = repository.getIdGenerator().fromString(id);
            try {
                Record record = repository.delete(recordId, postAction.getConditions());
                if (record != null && record.getResponseStatus() == ResponseStatus.CONFLICT) {
                    return Response.status(CONFLICT.getStatusCode()).entity(Entity.create(record, uriInfo)).build();
                }
            } catch (RecordNotFoundException e) {
                throw new ResourceException(e, NOT_FOUND.getStatusCode());
            } catch (Exception e) {
                throw new ResourceException("Error loading record.", e, INTERNAL_SERVER_ERROR.getStatusCode());
            }
            return Response.status(NO_CONTENT.getStatusCode()).build();
        } else {
            throw new ResourceException("Unsupported POST action: " + postAction.getAction(), BAD_REQUEST.getStatusCode());
        }
    }

    @DELETE
    public Response delete(@PathParam("id") String id) {
        RecordId recordId = repository.getIdGenerator().fromString(id);
        try {
            repository.delete(recordId);
        } catch (RecordNotFoundException e) {
            throw new ResourceException(e, NOT_FOUND.getStatusCode());
        } catch (Exception e) {
            throw new ResourceException("Error loading record.", e, INTERNAL_SERVER_ERROR.getStatusCode());
        }
        return Response.status(NO_CONTENT.getStatusCode()).build();
    }
}
