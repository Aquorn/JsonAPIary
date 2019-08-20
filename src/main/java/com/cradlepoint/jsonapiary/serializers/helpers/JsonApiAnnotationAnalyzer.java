package com.cradlepoint.jsonapiary.serializers.helpers;

import com.cradlepoint.jsonapiary.annotations.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonMappingException;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JsonApiAnnotationAnalyzer {

    ////////////////
    // Attributes //
    ////////////////

    private static final Map<Class<?>, Method> jsonObjectIdGetter = new HashMap<>();
    private static final Map<Class<?>, Method> jsonObjectIdSetter = new HashMap<>();
    private static final Map<Class<?>, Map<String, Method>> jsonObjectAttributeGetters = new HashMap<>();
    private static final Map<Class<?>, Map<String, Method>> jsonObjectAttributeSetters = new HashMap<>();

    private static final Class<? extends Annotation> CATCH_ALL_JSON_API_OBJECT = JsonApiMeta.class;

    /////////////////
    // Constructor //
    /////////////////

    /**
     * Private void constructor
     */
    private JsonApiAnnotationAnalyzer() { }

    ////////////////////
    // Public Methods //
    ////////////////////

    public static void cacheClass(Class<?> clazz) {
        for (Field field : clazz.getDeclaredFields()) {
            boolean isIdField = field.isAnnotationPresent(JsonApiId.class);
            boolean isAttributeField = field.isAnnotationPresent(JsonApiAttribute.class);

            if (!(isIdField || isAttributeField)) {
                continue;
            }

            String fieldName = field.getName();

            PropertyDescriptor pd = null;

            try {
                pd = new PropertyDescriptor(fieldName, clazz);
            } catch (IntrospectionException e) {
                throw new RuntimeException(String.format("%s.%s has json field annotation but could not acquire get/set methods", clazz.getName(), fieldName), e);
            }

            if (isIdField) {
                jsonObjectIdGetter.put(clazz, pd.getReadMethod());
                jsonObjectIdSetter.put(clazz, pd.getWriteMethod());
            }

            if (isAttributeField) {
                Map<String, Method> getters = jsonObjectAttributeGetters.computeIfAbsent(clazz, c -> new HashMap<>());
                Map<String, Method> setters = jsonObjectAttributeSetters.computeIfAbsent(clazz, c -> new HashMap<>());

                getters.put(fieldName, pd.getReadMethod());
                setters.put(fieldName, pd.getWriteMethod());
            }
        }
    }

    private static Method getIdMethod(Map<Class<?>, Method> map, Class<?> cls) {
        Method method = map.get(cls);

        if (method == null) {
            cacheClass(cls);
        }

        return map.get(cls);
    }

    public static Method getIdGetter(Class<?> cls) {
        return getIdMethod(jsonObjectIdGetter, cls);
    }

    public static Method getIdSetter(Class<?> cls) {
        return getIdMethod(jsonObjectIdSetter, cls);
    }

    public static Method getAttributeMethod(Map<Class<?>, Map<String, Method>> map, Class<?> cls, String fieldName) {
        Map<String, Method> methods = map.get(cls);

        if (methods == null) {
            cacheClass(cls);
        }

        methods = map.get(cls);

        return methods.get(fieldName);
    }

    public static Map<String, Method> getJsonGetters(Class<?> cls) {
        return jsonObjectAttributeGetters.get(cls);
    }

    public static Method getAttributeGetter(Class<?> cls, String fieldName) {
        return getAttributeMethod(jsonObjectAttributeGetters, cls, fieldName);
    }

    public static Map<String, Method> getJsonSetters(Class<?> cls) {
        return jsonObjectAttributeSetters.get(cls);
    }

    public static Method getAttributeSetter(Class<?> cls, String fieldName) {
        return getAttributeMethod(jsonObjectAttributeSetters, cls, fieldName);
    }

    public static class RelationshipStub {

        public static class InternalStub {
            private Object id;
            private String type;

            public InternalStub(Object id, String type) {
                this.id = id;
                this.type = type;
            }

            public Object getId() {
                return id;
            }

            public void setId(Object id) {
                this.id = id;
            }

            public String getType() {
                return type;
            }

            public void setType(String type) {
                this.type = type;
            }
        }

        private InternalStub data;

        public RelationshipStub(Object id, String type) {
            this.data = new InternalStub(id, type);
        }

        public InternalStub getData() {
            return data;
        }

        public void setData(InternalStub data) {
            this.data = data;
        }
    }

    /**
     * Returns a map of JSON API keys (Strings) and their unserialized value (Object), based on the annotations
     * on the the passed in Object.
     * @param jsonApiObject
     * @param annotation
     * @param jsonGenerator
     * @return
     * @throws IOException
     */
    public static Map<String, Object> fetchJsonsByAnnotation(
            Object jsonApiObject,
            Class<? extends Annotation> annotation,
            JsonGenerator jsonGenerator) throws IOException {
        Map<String, Object> jsons = new HashMap<String, Object>();
        boolean isAnnotationAlsoCatchAll = CATCH_ALL_JSON_API_OBJECT.equals(annotation);

        // First, fetch all the Fields //
        List<Field> completeFields = new ArrayList<Field>();
        Class type = jsonApiObject.getClass();
        while(type != null) {
            for(Field field : type.getDeclaredFields()) {
                completeFields.add(field);
            }
            type = type.getSuperclass();
        }

        boolean isIdRelationship = JsonApiIdRelationship.class.equals(annotation);
        boolean isIdRealtionshipContainer = JsonApiIdRelationshipContainer.class.equals(annotation);
//        boolean isIdRealtionshipContainer = false;

        for(Field field : completeFields) {
            if(field.isAnnotationPresent(annotation)) {


                if (isIdRelationship) {
                    JsonApiIdRelationship typeAnnotation = field.getAnnotation(JsonApiIdRelationship.class);

                    RelationshipStub relationshipStub = new RelationshipStub(fetchFieldValue(jsonApiObject, field, jsonGenerator), typeAnnotation.value());
                    jsons.put(fetchFieldKey(field, annotation), relationshipStub);

                } else if (isIdRealtionshipContainer) {
                    JsonApiIdRelationshipContainer typeAnnotation = field.getAnnotation(JsonApiIdRelationshipContainer.class);

//                    RelationshipStub relationshipStub = new RelationshipStub(fetchFieldValue(jsonApiObject, field, jsonGenerator), typeAnnotation.value());
//                    jsons.put(fetchFieldKey(field, annotation), relationshipStub);
                    if (typeAnnotation != null) {
                        Object value = fetchFieldValue(jsonApiObject, field, jsonGenerator);
                        if (value instanceof Map) {
                            Map<?,?> map = (Map) value;
                            Map<Object, RelationshipStub> relationshipStubMap = new HashMap<>();
//                            for (Map.Entry<?, ?> entry : map.entrySet()) {
////                                relationshipStubMap.put(entry.getKey(), new RelationshipStub(entry.getValue(), typeAnnotation.value()));
//                                relationshipStubMap.put(entry.getKey(), entry.getValue());
//                            }
//                            jsons.put(fetchFieldKey(field, annotation), relationshipStubMap);
                            jsons.put(fetchFieldKey(field, annotation), value);
                        } else {
                            throw new IllegalArgumentException("JsonApiIdRelationshipContainer annotation must be used on a map");
                        }
//                        jsons.put(
//                                fetchFieldKey(field, annotation),
//                                new RelationshipMap((Map<?,?>) fetchFieldValue(jsonApiObject, field, jsonGenerator)));
                    }

//                            fetchFieldValue(jsonApiObject, field, jsonGenerator));

//                    throw new IllegalArgumentException("JsonApiIdRelationshipContainer annotation must be used on a map");

                } else {
                    // This Field is EXPLICITLY part of the annotation //
                    jsons.put(
                            fetchFieldKey(field, annotation),
                            fetchFieldValue(jsonApiObject, field, jsonGenerator));
                }

            } else if(isOtherJsonApiAnnotationPresent(field.getDeclaredAnnotations(), annotation)) {
                // This Field is explicitly NOT part of the annotation //
                continue;
            } else if (field.isAnnotationPresent(JsonApiIgnore.class)) {
                // This Field is explicitly NOT to be included in the JsonAPI serialization
            } else if(isAnnotationAlsoCatchAll && field.isAnnotationPresent(JsonProperty.class)) {
                // This Field is IMPLICITLY part of the annotation //
                jsons.put(
                        fetchFieldKey(field, annotation),
                        fetchFieldValue(jsonApiObject, field, jsonGenerator));
            } else {
                // This Field is un-annotated //
            }
        }

        // Then Fetch all the Methods //
        List<Method> completeMethods = new ArrayList<Method>();
        type = jsonApiObject.getClass();
        while(type != null) {
            for(Method method : type.getDeclaredMethods()) {
                completeMethods.add(method);
            }
            type = type.getSuperclass();
        }

        for(Method method : completeMethods) {
            if(method.isAnnotationPresent(annotation)) {
                if (isIdRelationship) {
                    Map<String, Object> relationshipStub = new HashMap<>();
                    relationshipStub.put("id", fetchMethodValue(jsonApiObject, method, jsonGenerator));
                    relationshipStub.put("type", method.getAnnotation(JsonApiIdRelationship.class).value());

                    jsons.put(fetchMethodKey(method, annotation), relationshipStub);
                } else {
                    // This Method is EXPLICITLY part of the annotation //
                    jsons.put(
                            fetchMethodKey(method,  annotation),
                            fetchMethodValue(jsonApiObject, method, jsonGenerator));
                }

            } else if(isOtherJsonApiAnnotationPresent(method.getDeclaredAnnotations(), annotation)) {
                // This Method is explicitly NOT part of the annotation //
                continue;
            } else if (method.isAnnotationPresent(JsonApiIgnore.class)) {
                // This Field is explicitly NOT to be included in the JsonAPI serialization
            } else if(isAnnotationAlsoCatchAll && method.isAnnotationPresent(JsonProperty.class)) {
                // This Method is IMPLICITLY part of the annotation //
                jsons.put(
                        fetchMethodKey(method,  annotation),
                        fetchMethodValue(jsonApiObject, method, jsonGenerator));
            } else {
                // This Method is un-annotated //
            }
        }

        return jsons;
    }

    /////////////////////
    // Private Methods //
    /////////////////////

    private static String fetchFieldKey(
            Field field,
            Class<? extends Annotation> jsonApiAnnotation) {
        String key = null;

        // Check to see if there is a JsonAPI annotation with a value //
        if(jsonApiAnnotation == JsonApiAttribute.class && field.isAnnotationPresent(JsonApiAttribute.class)) {
            key = field.getAnnotation(JsonApiAttribute.class).value();
        } else if(jsonApiAnnotation == JsonApiLink.class && field.isAnnotationPresent(JsonApiLink.class)) {
            key = field.getAnnotation(JsonApiLink.class).value();
        } else if(jsonApiAnnotation == JsonApiMeta.class && field.isAnnotationPresent(JsonApiMeta.class)) {
            key = field.getAnnotation(JsonApiMeta.class).value();
        } else if(jsonApiAnnotation == JsonApiRelationship.class && field.isAnnotationPresent(JsonApiRelationship.class)) {
            key = field.getAnnotation(JsonApiRelationship.class).value();
        } else if (jsonApiAnnotation == JsonApiIdRelationship.class && field.isAnnotationPresent(JsonApiIdRelationship.class)) {
            key = field.getAnnotation(JsonApiIdRelationship.class).property();
        }

        if(key != null && !key.isEmpty()) {
            return key;
        }

        // Check to see if there is a value on a Jackson annotation //
        if(field.isAnnotationPresent(JsonProperty.class)) {
            key = field.getAnnotation(JsonProperty.class).value();
        }

        if(key != null && !key.isEmpty()) {
            return key;
        }

        // As a last resort, return the field name //
        return field.getName();
    }

    private static String fetchMethodKey(
            Method method,
            Class<? extends Annotation> jsonApiAnnotation) {
        String key = null;

        // Check to see if there is a JsonAPI annotation with a value //
        if(jsonApiAnnotation == JsonApiAttribute.class && method.isAnnotationPresent(JsonApiAttribute.class)) {
            key = method.getAnnotation(JsonApiAttribute.class).value();
        } else if(jsonApiAnnotation == JsonApiLink.class && method.isAnnotationPresent(JsonApiLink.class)) {
            key = method.getAnnotation(JsonApiLink.class).value();
        } else if(jsonApiAnnotation == JsonApiMeta.class && method.isAnnotationPresent(JsonApiMeta.class)) {
            key = method.getAnnotation(JsonApiMeta.class).value();
        } else if(jsonApiAnnotation == JsonApiRelationship.class && method.isAnnotationPresent(JsonApiRelationship.class)) {
            key = method.getAnnotation(JsonApiRelationship.class).value();
        }

        if(key != null && !key.isEmpty()) {
            return key;
        }

        // Check to see if there is a value on a Jackson annotation //
        if(method.isAnnotationPresent(JsonProperty.class)) {
            key = method.getAnnotation(JsonProperty.class).value();
        }

        if(key != null && !key.isEmpty()) {
            return key;
        }

        // As a last resort, return the field name //
        return method.getName();
    }

    private static Object fetchFieldValue(
            Object jsonApiObject,
            Field field,
            JsonGenerator jsonGenerator) throws IOException {
        try {
            return field.get(jsonApiObject);
        } catch (IllegalAccessException e) {
            // Noop...
        }

        Class type = jsonApiObject.getClass();
        while(type != null) {
            for(String methodName : generateGetterNames(field)) {
                try {
                    Method getter = type.getDeclaredMethod(methodName);
                    return getter.invoke(jsonApiObject);
                } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                    // Noop...
                }
            }
            type = type.getSuperclass();
        }

        String issue = "Unable to access value for field: " + field.getName() + " . The field is both private," +
                "and a default public void getter for it was not found!";
        throw JsonMappingException.from(jsonGenerator, issue);
    }

    private static List<String> generateGetterNames(
            Field field) {
        List<String> getterNames = new ArrayList<String>();

        // Add the obvious/simple getter name //
        String fieldName = field.getName();
        getterNames.add("get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1));

        // Add bool getter, if applicable //
        if(field.getType() == boolean.class || field.getType() == Boolean.class) {
            getterNames.add("is" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1));
        }

        return getterNames;
    }

    private static Object fetchMethodValue(
            Object jsonApiObject,
            Method method,
            JsonGenerator jsonGenerator) throws IOException {
        try {
            return method.invoke(jsonApiObject);
        } catch (IllegalAccessException | InvocationTargetException e) {
            // Noop...
        }

        String issue = "Unable to access json annotated method: " + method.getName() + " on type: " +
                jsonApiObject.getClass().getName() + " !!";
        throw JsonMappingException.from(jsonGenerator, issue);
    }

    private static boolean isOtherJsonApiAnnotationPresent(
            Annotation[] annotations,
            Class<? extends Annotation> annotation) {
        for(Annotation presentAnnotation : annotations) {
            if(!annotation.equals(presentAnnotation)) {
                if(presentAnnotation.annotationType().isAnnotationPresent(JsonApiProperty.class)) {
                    return true;
                }
            } else {
                return false;
            }
        }

        return false;
    }

}
