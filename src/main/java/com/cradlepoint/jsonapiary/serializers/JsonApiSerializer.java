package com.cradlepoint.jsonapiary.serializers;

import com.cradlepoint.jsonapiary.annotations.*;
import com.cradlepoint.jsonapiary.constants.JsonApiKeyConstants;
import com.cradlepoint.jsonapiary.enums.JsonApiObjectContext;
import com.cradlepoint.jsonapiary.serializers.helpers.JsonApiAnnotationAnalyzer;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class JsonApiSerializer {

    /////////////////
    // Constructor //
    /////////////////

    /**
     * Default void constructor
     */
    public JsonApiSerializer() { }

    ////////////////////
    // Public Methods //
    ////////////////////

    /**
     * Serializes any random Object by detecting if it is JsonAPI annotated or not, then serializing it accordingly
     * @param object
     * @param serializationContext
     * @param jsonGenerator
     * @param serializerProvider
     * @return
     * @throws IOException
     */
    public Set<Object> serializeRandomObject(
            Object object,
            JsonApiObjectContext serializationContext,
            JsonGenerator jsonGenerator,
            SerializerProvider serializerProvider) throws IOException {
        Set<Object> includes = new HashSet<Object>();

        switch (serializationContext) {
            case PRIMARY:
            case RELATIONSHIP:
            case RESOURCE_LINKAGE:
                if (object == null) {
                    jsonGenerator.writeNull();
                } else if (isObjectJsonApiObject(object)) {
                    includes.addAll(
                            this.serializeJsonApiObject(object, serializationContext, jsonGenerator, serializerProvider));
                } else if (isObjectJsonApiObjectList(object)) {
                    includes.addAll(
                            this.serializeJsonApiObjectList((List) object, serializationContext, jsonGenerator, serializerProvider));
                } else if (JsonApiAnnotationAnalyzer.RelationshipStub.class.equals(object.getClass())) {
                    JsonSerializer dataSerializer = serializerProvider.findValueSerializer(object.getClass());
                    dataSerializer.serialize(object, jsonGenerator, serializerProvider);
                } else if  (isObjectJsonApiObjectMap(object)) {
//                    jsonGenerator.writeStartObject();
//                    jsonGenerator.writeFieldName(JsonApiKeyConstants.RELATIONSHIPS_KEY);
                    includes.addAll(this.serializeJsonApiObjectMap((Map) object, serializationContext, jsonGenerator, serializerProvider));
//                    jsonGenerator.writeEndObject();
                } else {
                    String issue = "In order to be serialized in the \"" + serializationContext.toString() + "\" JsonAPI context" +
                            " object type " + object.getClass().getName() + " needs to be JsonAPIary annotated (@JsonApiType, " +
                            "amongst others).";
                    throw JsonMappingException.from(jsonGenerator, issue);
                }
                break;
            case LINK:
                if (object == null) {
                    jsonGenerator.writeNull();
                } else if(object.getClass() == URL.class) {
                    jsonGenerator.writeString(object.toString());
                } else {
                    try {
                        URL url = new URL(object.toString());
                        jsonGenerator.writeString(url.toString());
                    } catch(MalformedURLException e) {
                        String issue = "The value returned by the \"toString()\" method on the object of type: " +
                                object.getClass().getName() + " does not parse out into a valid URL. The other option " +
                                " is to decorate an attribute of type " + URL.class.getName() + " as the link.";
                        throw new IllegalArgumentException(issue, e);
                    }
                }
                break;
            case ATTRIBUTE:
            case META:
                if(object == null) {
                    jsonGenerator.writeNull();
                } else {
                    JsonSerializer dataSerializer = serializerProvider.findValueSerializer(object.getClass());
                    dataSerializer.serialize(object, jsonGenerator, serializerProvider);
                }
                break;
            default:
                String issue = "Unexpected JsonAPI context: \"" + serializationContext.toString() + "\" when serializing " +
                        object.getClass();
                throw JsonMappingException.from(jsonGenerator, issue);
        }

        return includes;
    }

    /**
     * Serializes sub-object according to JsonAPI spec
     * @param jsonApiObject
     * @param jsonGenerator
     * @param serializerProvider
     * @throws IOException
     */
    public Set<Object> serializeJsonApiObject(
            Object jsonApiObject,
            JsonApiObjectContext serializationContext,
            JsonGenerator jsonGenerator,
            SerializerProvider serializerProvider) throws IOException {
        // Round up the Attributes, Relationships, and MORE! //
        Map<String, Object> id =
                JsonApiAnnotationAnalyzer.fetchJsonsByAnnotation(jsonApiObject, JsonApiId.class, jsonGenerator);
        Map<String, Object> attributes =
                JsonApiAnnotationAnalyzer.fetchJsonsByAnnotation(jsonApiObject, JsonApiAttribute.class, jsonGenerator);
        Map<String, Object> links =
                JsonApiAnnotationAnalyzer.fetchJsonsByAnnotation(jsonApiObject, JsonApiLink.class, jsonGenerator);
        Map<String, Object> metas =
                JsonApiAnnotationAnalyzer.fetchJsonsByAnnotation(jsonApiObject, JsonApiMeta.class, jsonGenerator);
        Map<String, Object> relationships =
                JsonApiAnnotationAnalyzer.fetchJsonsByAnnotation(jsonApiObject, JsonApiRelationship.class, jsonGenerator);
        Map<String, Object> id_relationships =
                JsonApiAnnotationAnalyzer.fetchJsonsByAnnotation(jsonApiObject, JsonApiIdRelationship.class, jsonGenerator);
        Map<String, Object> id_container_relationships =
                JsonApiAnnotationAnalyzer.fetchJsonsByAnnotation(jsonApiObject, JsonApiIdRelationshipContainer.class, jsonGenerator);

        relationships.putAll(id_relationships);
        relationships.putAll(id_container_relationships);

        Set<Object> includes = new HashSet<Object>();

        jsonGenerator.writeStartObject();

        // Serialize out the ID and Type (a.k.a. "Resource Linkage" data) //
        switch (serializationContext) {
            case RELATIONSHIP:
                jsonGenerator.writeFieldName(JsonApiKeyConstants.DATA_KEY);
                jsonGenerator.writeStartObject();
                serializeIdAndType(jsonApiObject, id, jsonGenerator, serializerProvider);
                jsonGenerator.writeEndObject();
                includes.add(jsonApiObject);
                break;
            default:
                serializeIdAndType(jsonApiObject, id, jsonGenerator, serializerProvider);
        }

        // Serialize out the Attributes (if applicable) //
        switch(serializationContext) {
            case PRIMARY:
            case ATTRIBUTE:
            case META:
                includes.addAll(
                        serializeMap(attributes, JsonApiObjectContext.ATTRIBUTE, jsonGenerator, serializerProvider));
                break;
            default:
                // Not serialized in this context
        }

        // Serialize out the Links (if applicable) //
        switch(serializationContext) {
            case PRIMARY:
            case RELATIONSHIP:
            case META:
                includes.addAll(
                        serializeMap(links, JsonApiObjectContext.LINK, jsonGenerator, serializerProvider));
                break;
            default:
                // Not serialized in this context
        }

        // Serialize out the Meta //
        switch (serializationContext) {
            case PRIMARY:
            case ATTRIBUTE:
            case META:
                includes.addAll(
                        serializeMap(metas, JsonApiObjectContext.META, jsonGenerator, serializerProvider));
                break;
            default:
                // Not serialized in this context
        }

        // Serialize out the Relationships //
        switch (serializationContext) {
            case PRIMARY:
            case META:
                includes.addAll(
                        serializeMap(relationships, JsonApiObjectContext.RELATIONSHIP, jsonGenerator, serializerProvider));
                break;
            default:
                // Not serialized in this context
        }

        jsonGenerator.writeEndObject();

        return includes;
    }

    public Set<Object> serializeJsonApiObjectList(
            List<Object> jsonApiObjectList,
            JsonApiObjectContext serializationContext,
            JsonGenerator jsonGenerator,
            SerializerProvider serializerProvider) throws IOException {

        Set<Object> includes = new HashSet<Object>();

        switch(serializationContext) {
            case RELATIONSHIP:
                jsonGenerator.writeStartObject();
                jsonGenerator.writeFieldName(JsonApiKeyConstants.DATA_KEY);
                break;
            default:
                // No special serializations for this context
        }

        jsonGenerator.writeStartArray();
        for(Object element : jsonApiObjectList) {
            switch (serializationContext) {
                case RELATIONSHIP:
                    this.serializeJsonApiObject(
                            element,
                            JsonApiObjectContext.RESOURCE_LINKAGE,
                            jsonGenerator,
                            serializerProvider);
                    includes.add(element);
                    break;
                default:
                    includes.addAll(
                        this.serializeJsonApiObject(
                                element,
                                serializationContext,
                                jsonGenerator,
                                serializerProvider));
            }
        }
        jsonGenerator.writeEndArray();

        switch(serializationContext) {
            case RELATIONSHIP:
                jsonGenerator.writeEndObject();
                break;
            default:
                // No special serializations for this context
        }

        return includes;
    }

    public Set<Object> serializeJsonApiObjectMap(
            Map<Object, Object> jsonApiObjectMap,
            JsonApiObjectContext serializationContext,
            JsonGenerator jsonGenerator,
            SerializerProvider serializerProvider) throws IOException {

        Set<Object> includes = new HashSet<Object>();

        switch(serializationContext) {
            case RELATIONSHIP:
                jsonGenerator.writeStartObject();
                jsonGenerator.writeFieldName(JsonApiKeyConstants.DATA_KEY);
                break;
            default:
                // No special serializations for this context
        }

//        jsonGenerator.writeStartObject();
        jsonGenerator.writeStartArray();
        for(Map.Entry element : jsonApiObjectMap.entrySet()) {
//            jsonGenerator.writeFieldName(element.getKey().toString());
//            jsonGenerator.writeStartObject();
//            jsonGenerator.writeFieldName(JsonApiKeyConstants.DATA_KEY);

            switch (serializationContext) {
                case RELATIONSHIP:

                    this.serializeJsonApiObject(
                            element.getValue(),
                            JsonApiObjectContext.RESOURCE_LINKAGE,
//                            JsonApiObjectContext.RESOURCE_LINKAGE_MAP,
                            jsonGenerator,
                            serializerProvider);
                    includes.add(element.getValue());
                    break;
                default:
                    includes.addAll(
                            this.serializeJsonApiObject(
                                    element.getValue(),
                                    serializationContext,
                                    jsonGenerator,
                                    serializerProvider));
            }

//            jsonGenerator.writeEndObject();
        }
//        jsonGenerator.writeEndObject();
        jsonGenerator.writeEndArray();

        switch(serializationContext) {
            case RELATIONSHIP:
                jsonGenerator.writeEndObject();
                break;
            default:
                // No special serializations for this context
        }

        return includes;
    }

    /////////////////////
    // Private Methods //
    /////////////////////

    private void serializeIdAndTypeMap(Map<Object, Object> map, Map<String, Object> idMap, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        for (Map.Entry entry : map.entrySet()) {
            jsonGenerator.writeFieldName(entry.getKey().toString());
            jsonGenerator.writeStartObject();
            serializeIdAndType(entry.getValue(), idMap, jsonGenerator, serializerProvider);
            jsonGenerator.writeEndObject();
        }
    }

    private void serializeIdAndType(
            Object jsonApiObject,
            Map<String, Object> idMap,
            JsonGenerator jsonGenerator,
            SerializerProvider serializerProvider) throws IOException {
        // Serialize the ID //
        if(idMap.size() > 1) {
            String issue = "Found multiple Fields and/or Methods tagged with @JsonApiId in type: " +
                    jsonApiObject.getClass().getName() + " !!!";
            throw JsonMappingException.from(jsonGenerator, issue);
        } else if(idMap.size() == 1) {
            jsonGenerator.writeStringField(JsonApiKeyConstants.ID_KEY, idMap.get(idMap.keySet().toArray()[0]).toString());
        } else {
            String issue = "Type (" + jsonApiObject.getClass().getName() + ") does not contain a JsonAPI id! " +
                    "An attribute must be annotated with @JsonApiId!";
            throw new IllegalArgumentException(issue);
        }

        // Serialize the Type //
        JsonApiType typeAnnotation = jsonApiObject.getClass().getAnnotation(JsonApiType.class);
        if(typeAnnotation == null || typeAnnotation.value() == null || typeAnnotation.value().isEmpty()) {
            jsonGenerator.writeStringField(JsonApiKeyConstants.TYPE_KEY, jsonApiObject.getClass().getSimpleName());
        } else {
            jsonGenerator.writeStringField(JsonApiKeyConstants.TYPE_KEY, typeAnnotation.value());
        }
    }

    private Set<Object> serializeMap(
            Map<String, Object> map,
            JsonApiObjectContext serializationContext,
            JsonGenerator jsonGenerator,
            SerializerProvider serializerProvider) throws IOException {
        Set<Object> includes = new HashSet<Object>();

        if(map != null && !map.isEmpty()) {
            switch (serializationContext) {
                case ATTRIBUTE:
                    jsonGenerator.writeFieldName(JsonApiKeyConstants.ATTRIBUTES_KEY);
                    break;
                case LINK:
                    jsonGenerator.writeFieldName(JsonApiKeyConstants.LINKS_KEY);
                    break;
                case META:
                    jsonGenerator.writeFieldName(JsonApiKeyConstants.META_DATA_KEY);
                    break;
                case RELATIONSHIP:
                    jsonGenerator.writeFieldName(JsonApiKeyConstants.RELATIONSHIPS_KEY);
                    break;
                default:
                    String issue = "Unexpected object status: \"" + serializationContext.toString() + "\" when serializing map";
                    throw JsonMappingException.from(jsonGenerator, issue);
            }
            jsonGenerator.writeStartObject();

            for (String key : map.keySet()) {
                Object value = map.get(key);
                if(value != null) {
                    jsonGenerator.writeFieldName(key);

                    includes.addAll(
                            this.serializeRandomObject(value, serializationContext, jsonGenerator, serializerProvider));

                }
            }

            jsonGenerator.writeEndObject();
        }

        return includes;
    }

    private boolean isObjectJsonApiObject(
            Object object) {
        return object.getClass().isAnnotationPresent(JsonApiType.class);
    }

    private boolean isObjectJsonApiObjectMap(Object object) {
        if (object == null || !(object instanceof Map)) {
            return false;
        }

        Map map = (Map) object;

        if (map.isEmpty()) {
            return true;
        }

        boolean jsonApi = true;
        for (Object element : map.values()) {
            jsonApi &= element.getClass().isAnnotationPresent(JsonApiType.class);
        }

        return jsonApi;
    }

    private boolean isObjectJsonApiObjectList(
            Object object) {
        // Sanity Checks //
        if(object == null || !(object instanceof List)) {
            return false;
        }

        // Are *ALL* Objects in the list JsonAPI? //
        List list = (List) object;
        if(list.isEmpty()) {
            return true;
        }

        boolean jsonApi = true;
        for(Object element : list) {
            jsonApi &= element.getClass().isAnnotationPresent(JsonApiType.class);
        }

        return jsonApi;
    }

}
