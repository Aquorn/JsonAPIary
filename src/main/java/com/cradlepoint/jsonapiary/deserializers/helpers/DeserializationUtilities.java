package com.cradlepoint.jsonapiary.deserializers.helpers;

import com.cradlepoint.jsonapiary.annotations.*;
import com.cradlepoint.jsonapiary.constants.JsonApiKeyConstants;
import com.cradlepoint.jsonapiary.types.ResourceLinkage;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DeserializationUtilities {

    /////////////////
    // Constructor //
    /////////////////

    /**
     * Private void constructor
     */
    private DeserializationUtilities() { }

    ////////////////////
    // Public Methods //
    ////////////////////

    /**
     * Creates a ResourceLinkage from the contents of the passed in json
     * @param jsonNode
     * @param jsonApiTypeMap
     * @return
     */
    public static ResourceLinkage generateResourceLinkageFromNode(
            JsonNode jsonNode,
            Map<String, Class> jsonApiTypeMap) {
        // Determine Object type and construct //
        Class objectType = fetchJsonApiType(jsonNode, jsonApiTypeMap);
        Object id = fetchIdFromNode(objectType, jsonNode);

        return new ResourceLinkage(id, objectType);
    }

    /**
     * Generates a new Object (and populates the ID field) based on the received json node.
     * @param jsonNode
     * @param jsonApiTypeMap
     * @return
     */
    public static Object generateObjectFromNode(
            JsonNode jsonNode,
            Map<String, Class> jsonApiTypeMap)  {
        // Determine Object type and construct //
        Class objectType = fetchJsonApiType(jsonNode, jsonApiTypeMap);
        Object object = generateObjectFromType(objectType);

        // Set the ID on the Type //
        setIdOnType(objectType, object, jsonNode);

        return object;
    }

    /**
     * Returns the corresponding json key for the passed in Field
     * @param field
     * @return
     */
    public static String getFieldJsonKey(Field field) {
        if(field.isAnnotationPresent(JsonApiAttribute.class) && !(field.getAnnotation(JsonApiAttribute.class).value().isEmpty())) {
            return field.getAnnotation(JsonApiAttribute.class).value();
        } else if(field.isAnnotationPresent(JsonApiLink.class) && !(field.getAnnotation(JsonApiLink.class).value().isEmpty())) {
            return field.getAnnotation(JsonApiLink.class).value();
        } else if(field.isAnnotationPresent(JsonApiMeta.class) && !(field.getAnnotation(JsonApiMeta.class).value().isEmpty())) {
            return field.getAnnotation(JsonApiMeta.class).value();
        } else if(field.isAnnotationPresent(JsonApiRelationship.class) && !(field.getAnnotation(JsonApiRelationship.class).value().isEmpty())) {
            return field.getAnnotation(JsonApiRelationship.class).value();
        } else if(field.isAnnotationPresent(JsonProperty.class) && !(field.getAnnotation(JsonProperty.class).value().isEmpty())) {
            return field.getAnnotation(JsonProperty.class).value();
        } else {
            return field.getName();
        }
    }

    /**
     * Returns the corresponding json key for the passed in Method
     * @param method
     * @return
     */
    public static String getMethodJsonKey(Method method) {
        if(method.isAnnotationPresent(JsonApiAttribute.class) && !(method.getAnnotation(JsonApiAttribute.class).value().isEmpty())) {
            return method.getAnnotation(JsonApiAttribute.class).value();
        } else if(method.isAnnotationPresent(JsonApiLink.class) && !(method.getAnnotation(JsonApiLink.class).value().isEmpty())) {
            return method.getAnnotation(JsonApiLink.class).value();
        } else if(method.isAnnotationPresent(JsonApiMeta.class) && !(method.getAnnotation(JsonApiMeta.class).value().isEmpty())) {
            return method.getAnnotation(JsonApiMeta.class).value();
        } else if(method.isAnnotationPresent(JsonApiRelationship.class) && !(method.getAnnotation(JsonApiRelationship.class).value().isEmpty())) {
            return method.getAnnotation(JsonApiRelationship.class).value();
        } else if(method.isAnnotationPresent(JsonProperty.class) && !(method.getAnnotation(JsonProperty.class).value().isEmpty())) {
            return method.getAnnotation(JsonProperty.class).value();
        } else {
            return method.getName();
        }
    }

    /**
     * Sets the Passed in Field, on the passed in Object, to the passed in Value(/Object)
     * @param field
     * @param fieldValue
     * @param onto
     */
    public static void setObjectOnField(
            Field field,
            Object fieldValue,
            Object onto) {
        // First try to set the Field directly //
        try {
            field.set(onto, fieldValue);
            return;
        } catch (IllegalAccessException e) {
            // Non-public field... look for a setter!
        }

        // Look for a "setter" for the field //
        List<Method> completeMethods = new ArrayList<Method>();
        Class type = onto.getClass();
        while (type != null) {
            for(Method method : type.getDeclaredMethods()) {
                completeMethods.add(method);
            }
            type = type.getSuperclass();
        }

        Method setterMethod = null;
        for(Method method : completeMethods) {
            JsonProperty jsonPropertyAnnotation = method.getAnnotation(JsonProperty.class);
            if(method.getName().startsWith("set") && jsonPropertyAnnotation != null && jsonPropertyAnnotation.value().equals(getFieldJsonKey(field))) {
                setterMethod = method;
                break;
            } else if(method.getName().equals("set" + field.getName().substring(0, 1).toUpperCase() + field.getName().substring(1))) {
                setterMethod = method;
                break;
            }
        }

        if(setterMethod == null) {
            String issue = "Unable to set value on " + onto.getClass().getName() + " corresponding to json key " +
                    getFieldJsonKey(field) + ". Both that Field is not public, and no corresponding \"setter\" method " +
                    "for the Field was found!";
            throw new IllegalStateException(issue);
        }

        // Attempt to invoke detected "setter" //
        try {
            setterMethod.invoke(onto, fieldValue);
            return;
        } catch(IllegalAccessException | InvocationTargetException e) {
            String issue = "Unable to set value on " + onto.getClass().getName() + " corresponding to json key " +
                    getFieldJsonKey(field) + ". Both that Field is not public, and when invoking the detected \"setter\"" +
                    "method (" + setterMethod.getName() + ") ran into an issue!";
            throw new IllegalStateException(issue);
        }
    }

    /**
     * Invokes the Passed in ("setter") Method, on the passed in Object, with the passed in Value(/Object) as the
     * argument.
     * @param method
     * @param fieldValue
     * @param onto
     */
    public static void setObjectOnMethod(
            Method method,
            Object fieldValue,
            Object onto) {
        // Attempt to invoke detected "setter" //
        try {
            method.invoke(onto, fieldValue);
            return;
        } catch(IllegalAccessException | InvocationTargetException e) {
            String issue = "Unable to set value on " + onto.getClass().getName() + " corresponding to json key " +
                    getMethodJsonKey(method) + ". Invoking \"setter\" method (" + method.getName() +
                    ") ran into an issue!";
            throw new IllegalStateException(issue);
        }
    }

    /////////////////////
    // Private Methods //
    /////////////////////

    /**
     * Returns the type (Class) from the "type" key on the node.
     * @param node
     * @return
     */
    private static Class fetchJsonApiType(
            JsonNode node,
            Map<String, Class> jsonApiTypeMap) {
        String type = null;
        if(node.has(JsonApiKeyConstants.TYPE_KEY)) {
            type = node.get(JsonApiKeyConstants.TYPE_KEY).asText();
        } else {
            String issue = "Received JSON does not contain a \"" + JsonApiKeyConstants.TYPE_KEY + "\" key/value pair" +
                    " in the \"" + JsonApiKeyConstants.DATA_KEY + "\" block!";
            throw new IllegalStateException(issue);
        }

        if(!jsonApiTypeMap.containsKey(type)) {
            String issue = "Unknown/Unexpected type: " + type + " ... type must be annotated and registered with" +
                    " the JsonApiModule!";
            throw new IllegalStateException(issue);
        }

        return jsonApiTypeMap.get(type);
    }

    /**
     * Generates new object from type via void constructor
     * @param type
     * @return
     */
    private static Object generateObjectFromType(
            Class type)  {
        try {
            Constructor constructor = type.getConstructor();
            return constructor.newInstance();
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
            String issue = "No public default void constructor found on type: " + type.getName();
            throw new IllegalStateException(issue, e);
        }
    }

    /**
     * Returns the ID of the JsonAPI json
     * @param objectType
     * @param jsonNode
     * @return
     */
    private static Object fetchIdFromNode(
            Class objectType,
            JsonNode jsonNode) {
        // Fetch the ID object from the json, as the correct object type //
        Field idField = null;
        Method idMethod = null;
        Class type = objectType;
        while(type != null) {
            for (Field field : type.getDeclaredFields()) {
                if (field.isAnnotationPresent(JsonApiId.class)) {
                    idField = field;
                    break;
                }
            }
            if (idField == null) {
                for (Method method : type.getMethods()) {
                    if (method.isAnnotationPresent(JsonApiId.class)) {
                        idMethod = method;
                        break;
                    }
                }
            }
            type = type.getSuperclass();
        }

        // Sanity Check //
        if(idField == null && idMethod == null) {
            String issue = "No \"id\" found in the json when deserializing type: " + objectType.getName();
            throw new IllegalArgumentException(issue);
        }

        // Deserialize the ID //
        Object id = null;
        Class idType = idField != null ? idField.getType() : idMethod.getReturnType();
        JsonNode idNode = jsonNode.get(JsonApiKeyConstants.ID_KEY);
        if(idNode == null || idNode.isNull()) {
            id = null;
        } else if(idType == String.class) {
            id = idNode.asText();
        } else if (idType == UUID.class) {
            String str = idNode.asText();
            id = !str.isEmpty() ? UUID.fromString(idNode.asText()) : null;
        } else if(idType == Integer.class || idType == int.class) {
            id = idNode.asInt();
        } else if(idType == Long.class || idType == long.class) {
            id = idNode.asLong();
        } else if(idType == Float.class || idType == float.class) {
            id = idNode.floatValue();
        } else if(idType == Double.class || idType == double.class) {
            id = idNode.asDouble();
        } else {
            String issue = "Unknown/Unexpected type for \"id\" field on type: " + objectType.getName() + " ... " +
                    "The only accepted types are String, Integer, Long, Float, and Double.";
            throw new IllegalStateException(issue);
        }

        return id;
    }

    /**
     * Set the ID on the Java object from the ID in the JsonAPI json
     * @param objectType
     * @param object
     * @param jsonNode
     */
    private static void setIdOnType(
            Class objectType,
            Object object,
            JsonNode jsonNode) {
        // Fetch the ID object from the json, as the correct object type //
        Field idField = null;
        Class type = objectType;
        while(type != null) {
            for (Field field : type.getDeclaredFields()) {
                if (field.isAnnotationPresent(JsonApiId.class)) {
                    idField = field;
                    break;
                }
            }
            type = type.getSuperclass();
        }

        Object id = fetchIdFromNode(objectType, jsonNode);

        // Attempt to set the ID on the Field directly //
        try {
            idField.set(object, id);
            return;
        } catch (IllegalAccessException|NullPointerException e){
            // Unable to set on the Field directly... time to look for a "setter"
        }

        // Look for "setter" Method //
        Method idSetMethod = null;
        type = objectType;
        while (type != null) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.getName().startsWith("set") && method.isAnnotationPresent(JsonApiId.class)) {
                    idSetMethod = method;
                    break;
                } else if (idField != null) {
                    if (method.getName().equals("set" + idField.getName().substring(0, 1).toUpperCase() + idField.getName().substring(1))) {
                        idSetMethod = method;
                        break;
                    }
                }
            }
            type = type.getSuperclass();
        }

        if(idSetMethod == null) {
            String issue = "The @JsonApiId field is both not accessable (not public), AND a \"setter\" Method " +
                    "count not be found on type: " + objectType.getName() + ". A \"setter\" Method must have either the " +
                    "@JsonApiId annotation, or be named set{IdFieldName}(...).";
            throw new IllegalStateException(issue);
        }

        // Attempt to set the ID via the found Method //
        try {
            idSetMethod.invoke(object, id);
        } catch(IllegalAccessException | InvocationTargetException e) {
            String issue = "The @JsonApiId field is both not accessable (not public), AND neither is the detected " +
                    "\"setter\" Method!";
            throw new IllegalStateException(issue);
        }
    }

}
