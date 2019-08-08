package com.cradlepoint.jsonapiary.serializers;

import com.cradlepoint.jsonapiary.constants.JsonApiKeyConstants;
import com.cradlepoint.jsonapiary.enums.JsonApiObjectContext;
import com.cradlepoint.jsonapiary.envelopes.JsonApiEnvelope;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
import java.net.URL;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class JsonApiEnvelopeSerializer extends StdSerializer<JsonApiEnvelope> {

    ////////////////
    // Attributes //
    ////////////////

    private JsonApiSerializer jsonApiSerializer;

    /////////////////
    // Constructor //
    /////////////////

    /**
     * Default void constructors
     */
    public JsonApiEnvelopeSerializer() {
        super(JsonApiEnvelope.class);
        jsonApiSerializer = new JsonApiSerializer();
    }

    ///////////////////////////
    // StdSerializer Methods //
    ///////////////////////////

    /**
     * Serializes enveloped, and properly annotated, object according to JsonAPI specification
     * @param jsonApiEnvelope
     * @param jsonGenerator
     * @param serializerProvider
     * @throws IOException
     */
    public void serialize(
            JsonApiEnvelope jsonApiEnvelope,
            JsonGenerator jsonGenerator,
            SerializerProvider serializerProvider) throws IOException {
//        Set<Object> includes = new HashSet<Object>();
        Set<Object> includes = jsonApiEnvelope.flattenIncludes();

        // Create the JsonAPI "envelope" object, the call down expanding the "has-a" objects accordingly //
        jsonGenerator.writeStartObject();
        jsonGenerator.writeFieldName(JsonApiKeyConstants.DATA_KEY);

        Object data = jsonApiEnvelope.getData();
        if(data == null) {
            jsonGenerator.writeNull();
        } else {
            includes.addAll(
                    jsonApiSerializer.serializeRandomObject(
                            data,
                            JsonApiObjectContext.PRIMARY,
                            jsonGenerator,
                            serializerProvider));
        }

        // Write out the expanded "included" objects //
        if(!includes.isEmpty()) {
            Set<Object> currentIncludes = new HashSet<Object>();
            currentIncludes.addAll(includes);
            includes.add(jsonApiEnvelope.getData()); // To prevent the "data" object from being re-serialized

            Set<Object> newIncludes = new HashSet<Object>();

            jsonGenerator.writeFieldName(JsonApiKeyConstants.INCLUDED_KEY);
            jsonGenerator.writeStartArray();

            while(!currentIncludes.isEmpty()) {
                for (Object thingToInclude : currentIncludes) {
                    newIncludes.addAll(jsonApiSerializer.serializeRandomObject(
                            thingToInclude,
                            JsonApiObjectContext.PRIMARY,
                            jsonGenerator,
                            serializerProvider));
                }

                currentIncludes = determineNewNewIncludes(includes, newIncludes);
            }

            jsonGenerator.writeEndArray();
        }

        // Write out the top-level Links //
        if(!jsonApiEnvelope.getLinks().isEmpty()) {
            Map<String, URL> links = jsonApiEnvelope.getLinks();

            jsonGenerator.writeFieldName(JsonApiKeyConstants.LINKS_KEY);
            jsonGenerator.writeStartObject();
            for(String key : links.keySet()) {
                jsonGenerator.writeStringField(key, links.get(key).toString());
            }
            jsonGenerator.writeEndObject();
        }

        // Write out the top-level Meta //
        if(!jsonApiEnvelope.getMeta().isEmpty()) {
            Map<String, String> meta = jsonApiEnvelope.getMeta();

            jsonGenerator.writeFieldName(JsonApiKeyConstants.META_DATA_KEY);
            jsonGenerator.writeStartObject();
            for(String key : meta.keySet()) {
                jsonGenerator.writeStringField(key, meta.get(key));
            }
            jsonGenerator.writeEndObject();
        }

        jsonGenerator.writeEndObject();
    }

    /////////////////////
    // Private Methods //
    /////////////////////

    private Set<Object> determineNewNewIncludes(
            Set<Object> includes,
            Set<Object> newIncludes) {
        Set<Object> newNewIncludes = new HashSet<Object>();

        for(Object newInclude : newIncludes) {
            if(!includes.contains(newInclude)) {
                includes.add(newInclude);
                newNewIncludes.add(newInclude);
            }
        }
        newIncludes.clear();

        return newNewIncludes;
    }

}
