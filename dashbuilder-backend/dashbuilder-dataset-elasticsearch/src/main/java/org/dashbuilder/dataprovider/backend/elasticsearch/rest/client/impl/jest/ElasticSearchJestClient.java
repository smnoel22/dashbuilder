/**
 * Copyright (C) 2014 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.dashbuilder.dataprovider.backend.elasticsearch.rest.client.impl.jest;

import com.google.gson.*;
import io.searchbox.client.JestClient;
import io.searchbox.client.JestClientFactory;
import io.searchbox.client.JestResult;
import io.searchbox.client.config.HttpClientConfig;
import io.searchbox.core.Count;
import io.searchbox.core.CountResult;
import io.searchbox.indices.mapping.GetMapping;
import org.dashbuilder.dataprovider.backend.elasticsearch.rest.client.ElasticSearchClient;
import org.dashbuilder.dataprovider.backend.elasticsearch.rest.client.exception.ElasticSearchClientGenericException;
import org.dashbuilder.dataprovider.backend.elasticsearch.rest.client.impl.jest.gson.FieldMapping;
import org.dashbuilder.dataprovider.backend.elasticsearch.rest.client.model.*;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Named;
import java.lang.reflect.Type;
import java.util.*;

/**
 * <p>The Jest/GSON client for ElasticSearch server.</p>
 
 * <p>Usage:</p>
 * <ul>
 *     <li><a href="https://github.com/searchbox-io/Jest/tree/master/jest">https://github.com/searchbox-io/Jest/tree/master/jest</a></li>
 *     <li><a href="http://www.ibm.com/developerworks/java/library/j-javadev2-24/index.html?ca=drs-">http://www.ibm.com/developerworks/java/library/j-javadev2-24/index.html?ca=drs-</a></li>
 *     <li><a href="https://sites.google.com/site/gson/gson-user-guide">https://sites.google.com/site/gson/gson-user-guide</a></li>
 * </ul> 
 * 
 * @see <a href="https://github.com/searchbox-io/Jest">https://github.com/searchbox-io/Jest</a>
 */
@ApplicationScoped
@Named("elasticsearchJestClient")
public class ElasticSearchJestClient implements ElasticSearchClient<ElasticSearchJestClient> {

    protected String serverURL;
    protected String clusterName;
    protected String[] index;
    protected String[] type;
    // Defaults to 30sec.
    protected int timeout = 30000;
    
    // JestClient is designed to be singleton, don't construct it for each request.
    private JestClient client;

    public ElasticSearchJestClient() {
    }

    @Override
    public ElasticSearchJestClient serverURL(String serverURL) {
        this.serverURL = serverURL;
        if (clusterName != null) buildClient();
        return this;
    }

    @Override
    public ElasticSearchJestClient index(String... indexes) {
        this.index = indexes;
        if (serverURL != null && clusterName != null) buildClient();
        return this;
    }

    @Override
    public ElasticSearchJestClient type(String... types) {
        this.type = types;
        if (serverURL != null && clusterName != null) {
            if (index == null) throw new IllegalArgumentException("You cannot call elasticsearchRESTEasyClient#type before calling elasticsearchRESTEasyClient#index."); 
            buildClient();
        }
        return this;
    }

    @Override
    public ElasticSearchJestClient clusterName(String clusterName) {
        this.clusterName = clusterName;
        if (serverURL != null) buildClient();
        return this;
    }

    @Override
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }
    
    @Override
    public MappingsResponse getMappings(String... index) throws ElasticSearchClientGenericException {
        if (client == null) throw new IllegalArgumentException("elasticsearchRESTEasyClient instance is not build.");

        try {
            IndexMappingResponse[] result = new IndexMappingResponse[index.length];
            int x = 0;
            for (String _index : index) {
                IndexMappingResponse indexMappings = getMappings(_index, null);
                result[x++]  = indexMappings;
            }
            return new MappingsResponse(200, result);
        } catch (Exception e) {
            throw  new ElasticSearchClientGenericException("Cannot obtain mappings.", e);
        }
    }

    protected IndexMappingResponse getMappings(String index, String type) throws Exception{
        GetMapping.Builder builder = new GetMapping.Builder().addIndex(index);
        if (type != null) builder = builder.addType(type);
        
        GetMapping getMapping = builder.build();
        JestResult result = client.execute(getMapping);
        Set<Map.Entry<String, JsonElement>> mappings = result.getJsonObject().get(index).getAsJsonObject().get("mappings").getAsJsonObject().entrySet();
        TypeMappingResponse[] types = new TypeMappingResponse[mappings.size()];
        int x = 0;
        for (Map.Entry<String, JsonElement> entry : mappings) {
            String typeName = entry.getKey();
            JsonElement typeMappings = entry.getValue();
            JsonElement properties = typeMappings.getAsJsonObject().get("properties");
            Set<Map.Entry<String, JsonElement>> propertyMappings = properties.getAsJsonObject().entrySet();
            FieldMappingResponse[] fields = new FieldMappingResponse[propertyMappings.size()];
            int y = 0;
            for (Map.Entry<String, JsonElement> propertyMapping : propertyMappings) {
                String field = propertyMapping.getKey();
                FieldMapping fieldMappings = new Gson().fromJson(propertyMapping.getValue(), FieldMapping.class);
                FieldMappingResponse.FieldType fieldType = null;
                if (fieldMappings.getType() != null) fieldType = FieldMappingResponse.FieldType.valueOf(fieldMappings.getType().toUpperCase());
                FieldMappingResponse.IndexType indexType = null;
                if (fieldMappings.getIndex() != null) indexType = FieldMappingResponse.IndexType.valueOf(fieldMappings.getIndex().toUpperCase());
                String format = fieldMappings.getFormat();
                FieldMappingResponse fieldMappingResponse = new FieldMappingResponse(field, fieldType, indexType, format);
                fields[y++] = fieldMappingResponse;
            }
            TypeMappingResponse typeMappingResponse = new TypeMappingResponse(typeName, fields);
            types[x++] = typeMappingResponse;
        }
        return new IndexMappingResponse(index, types);
    }

    @Override
    public CountResponse count(String[] index, String... type) throws ElasticSearchClientGenericException {
        if (client == null) throw new IllegalArgumentException("elasticsearchRESTEasyClient instance is not build.");

        Count.Builder countBuilder = new Count.Builder().addIndex(Arrays.asList(index)); 
        if (type != null) countBuilder = countBuilder.addType(Arrays.asList(type));
        Count count = countBuilder.build();
        try {
            CountResult result = client.execute(count);

            double hitCount = result.getCount();
            int totalShards = result.getJsonObject().get("_shards").getAsJsonObject().get("total").getAsInt();
            return new CountResponse((long)hitCount, totalShards);
        } catch (Exception e) {
            throw new ElasticSearchClientGenericException("Cannot count.", e);
        }
    }

    @Override
    public SearchResponse search(SearchRequest request) throws ElasticSearchClientGenericException {
        if (client == null) throw new IllegalArgumentException("elasticsearchRESTEasyClient instance is not build.");

        Query query = request.getQuery();
        
        
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(Query.class, new QuerySerializer());
        builder.registerTypeAdapter(SearchResponse.class, new SearchResponseDeserializer());
        builder.registerTypeAdapter(SearchHitResponse.class, new HitDeserializer());
        Gson gson = builder.create();
        String gsonResult = gson.toJson(query);

        // TODO
        return null;
    }

    protected static class SearchResponseDeserializer implements JsonDeserializer<SearchResponse> {

        @Override
        public SearchResponse deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            SearchResponse result = null;
            if (typeOfT.equals(SearchResponse.class)) {
                JsonObject responseObject = json.getAsJsonObject();
                
                if (responseObject != null) {
                    long tookInMillis = responseObject.get("took").getAsLong();
                    int responseStatus = 200;

                    JsonObject shardsObject = responseObject.getAsJsonObject("_shards");
                    int totalShards = shardsObject.get("total").getAsInt();
                    int successfulShards = shardsObject.get("successful").getAsInt();
                    int shardFailures = shardsObject.get("failed").getAsInt();

                    long totalHits = 0;
                    float maxScore = 0;
                    List<String> columnIds = new LinkedList<String>();
                    List<SearchHitResponse> hits = new LinkedList<SearchHitResponse>(); 
                    JsonObject hitsObject = responseObject.getAsJsonObject("hits");
                    if (hitsObject != null) {
                        totalHits = hitsObject.get("total").getAsLong();
                        maxScore = hitsObject.get("max_score").getAsFloat();
                        JsonArray hitsArray = hitsObject.getAsJsonArray("hits");
                        if (hitsArray != null && hitsArray.size() > 0) {
                            for (int i = 0; i < hitsArray.size() ; i++) {
                                JsonElement hitResponseElement = hitsArray.get(i);
                                SearchHitResponse hit = context.deserialize(hitResponseElement, SearchHitResponse.class);
                                hits.add(hit);
                            }
                        }
                        
                        // Obtain the resulting column ids and types from the first hit.
                        if (!hits.isEmpty()) {
                            SearchHitResponse hit = hits.get(0);

                            Map<String, Object> fields = hit.getFields();
                            if (fields != null) {
                                Set<String> fieldNames = fields.keySet();
                                if (!fieldNames.isEmpty()) {
                                    for (String fieldName : fieldNames) {
                                        columnIds.add(fieldName);
                                    }
                                }
                            }
                        }
                    }
                    
                    result = new SearchResponse(tookInMillis, responseStatus, totalHits, maxScore, totalShards, successfulShards, shardFailures, columnIds, hits.toArray(new SearchHitResponse[hits.size()]));
                }
            }
            
            return result;
        }
    }

    protected static class HitDeserializer implements JsonDeserializer<SearchHitResponse> {

        @Override
        public SearchHitResponse deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            SearchHitResponse result = null;
            if (typeOfT.equals(SearchHitResponse.class)) {

                JsonObject hitObject = (JsonObject) json;
                float score = hitObject.get("_score").getAsFloat();
                String index = hitObject.get("_index").getAsString();
                String id = hitObject.get("_id").getAsString();
                String type = hitObject.get("_type").getAsString();
                long version = 0;
                Map<String ,Object> fields = new HashMap<String, Object>();
                JsonObject source = hitObject.getAsJsonObject("_source");
                if (source != null) {
                    Set<Map.Entry<String, JsonElement>> _fields = source.entrySet();
                    for (Map.Entry<String, JsonElement> field : _fields) {
                        String fieldName = field.getKey();
                        String fieldValue = field.getValue().getAsString();
                        fields.put(fieldName, fieldValue);
                    }
                    
                }
                result = new SearchHitResponse(score, index, id, type, version, fields);
            }
            
            return result;
        }
    }

    protected static class QuerySerializer implements JsonSerializer<Query> {
        public static final String SEARCH_API_FIELD = "field";
        public static final String SEARCH_API_EXISTS = "exists";
        public static final String SEARCH_API_TERM = "term";
        public static final String SEARCH_API_LT = "lt";
        public static final String SEARCH_API_LTE = "lte";
        public static final String SEARCH_API_GT = "gt";
        public static final String SEARCH_API_GTE = "gte";
        public static final String SEARCH_API_RANGE = "range";
        public static final String SEARCH_API_AND = "and";
        public static final String SEARCH_API_OR = "or";
        public static final String SEARCH_API_NOT  = "not";
        public static final String SEARCH_API_FILTER = "filter";
        public static final String SEARCH_API_QUERY = "query";
        public static final String SEARCH_API_MATCH = "match";
        public static final String SEARCH_API_MATCH_ALL = "match_all";
        public static final String SEARCH_API_MUST = "must";
        public static final String SEARCH_API_MUST_NOT = "must_not";
        public static final String SEARCH_API_SHOULD = "should";
        public static final String SEARCH_API_BOOL = "bool";
        private Query query;
        private static Gson gson = new GsonBuilder().create();

        public JsonObject serialize(Query src, Type typeOfSrc, JsonSerializationContext context) {
            this.query = src;
            
            JsonObject result = new JsonObject();
            JsonObject subResult = translate(query);
            String searchkey = isFilter(subResult) ? SEARCH_API_FILTER : SEARCH_API_QUERY; 
            result.add(searchkey, subResult);
            return result;
        }
        
        private boolean isFilter(JsonObject object) {
            if (object == null) return false;
            String serializedObject = gson.toJson(object).trim();
            boolean isTermQuery = serializedObject.startsWith("{\"" + SEARCH_API_TERM);
            boolean isRangeQuery = serializedObject.startsWith("{\"" + SEARCH_API_RANGE);
            boolean isExistsQuery = serializedObject.startsWith("{\"" + SEARCH_API_EXISTS);
            boolean isNotQuery = serializedObject.startsWith("{\"" + SEARCH_API_NOT);
            boolean isOrQuery = serializedObject.startsWith("{\"" + SEARCH_API_OR);
            boolean isAndQuery = serializedObject.startsWith("{\"" + SEARCH_API_AND);            
            return isTermQuery || isRangeQuery || isExistsQuery || isNotQuery || isOrQuery || isAndQuery;
        }

        private JsonObject translate(Query query) {
            if (query == null) return null;

            Query.Type type = query.getType();

            JsonObject result = null;

            switch (type) {
                case BOOL:
                    return translateBool(query);
                case MATCH:
                    return translateMatch(query);
                case MATCH_ALL:
                    return translateMatchAll(query);
                case FILTERED:
                    return translateFiltered(query);
                case AND:
                    return translateAnd(query);
                case OR:
                    return translateOr(query);
                case NOT:
                    return translateNot(query);
                case EXISTS:
                    return translateExists(query);
                case TERM:
                    return translateTerm(query);
                case RANGE:
                    return translateRange(query);
            }

            return result;
        }


        private JsonObject translateExists(Query query) {
            if (query == null) return null;

            String field = query.getField();
            JsonObject result = new JsonObject();
            JsonObject subResult = new JsonObject();
            subResult.addProperty(SEARCH_API_FIELD, field);
            result.add(SEARCH_API_EXISTS, subResult);
            return result;
        }

        private JsonObject translateTerm(Query query) {
            if (query == null) return null;

            String field = query.getField();
            Object value = query.getParam(Query.Parameter.VALUE.name());
            JsonObject result = new JsonObject();
            JsonObject subResult = new JsonObject();
            subResult.addProperty(field, (String) value);
            result.add(SEARCH_API_TERM, subResult);
            return result;
        }

        private JsonObject translateRange(Query query) {
            if (query == null) return null;

            String field = query.getField();
            JsonObject result = new JsonObject();

            JsonObject subResult = new JsonObject();
            addPrimitiveProperty(subResult, SEARCH_API_LT, query.getParam(Query.Parameter.LT.name()));
            addPrimitiveProperty(subResult, SEARCH_API_LTE, query.getParam(Query.Parameter.LTE.name()));
            addPrimitiveProperty(subResult, SEARCH_API_GT, query.getParam(Query.Parameter.GT.name()));
            addPrimitiveProperty(subResult, SEARCH_API_GTE, query.getParam(Query.Parameter.GTE.name()));
            JsonObject subObject = new JsonObject();
            subObject.add(field, subResult);
            result.add(SEARCH_API_RANGE, subObject);
            return result;
        }

        private void addPrimitiveProperty(JsonObject object, String key, Object value) {
            if (value != null) {
                if (value instanceof Number) {
                    object.addProperty(key, (Number) value);
                } else {
                    // TODO: Format date using "format" attribute for field or default EL expected format.
                    object.addProperty(key, value.toString());
                }
            }
        }

        private JsonObject translateAnd(Query query) {
            if (query == null) return null;

            JsonObject result = new JsonObject();
            JsonElement filterObjects = null;
            try {
                filterObjects = translateGsonQueries((List<Query>) query.getParam(Query.Parameter.FILTERS.name()));
            } catch (ClassCastException e) {
                filterObjects = translate((Query) query.getParam(Query.Parameter.FILTERS.name()));
            }
            result.add(SEARCH_API_AND, filterObjects);
            return result;
        }

        private JsonObject translateOr(Query query) {
            if (query == null) return null;

            JsonObject result = new JsonObject();
            JsonElement filterObjects = null;
            try {
                filterObjects = translateGsonQueries((List<Query>) query.getParam(Query.Parameter.FILTERS.name()));
            } catch (ClassCastException e) {
                filterObjects = translate((Query) query.getParam(Query.Parameter.FILTERS.name()));
            }
            result.add(SEARCH_API_OR, filterObjects);
            return result;
        }

        private JsonObject translateNot(Query query) {
            if (query == null) return null;

            JsonObject result = new JsonObject();
            JsonElement filterObjects = null;
            try {
                filterObjects = translateGsonQueries((List<Query>) query.getParam(Query.Parameter.FILTER.name()));
            } catch (ClassCastException e) {
                filterObjects = translate((Query) query.getParam(Query.Parameter.FILTER.name()));
            }
            result.add(SEARCH_API_NOT, filterObjects);
            return result;
        }

        private JsonObject translateFiltered(Query query) {
            if (query == null) return null;

            Query _query = (Query) query.getParam(Query.Parameter.QUERY.name());
            Query filter = (Query) query.getParam(Query.Parameter.FILTER.name());

            JsonObject queryObject = translate(_query);
            JsonObject filterObject = translate(filter);

            JsonObject result = new JsonObject();
            result.add(SEARCH_API_QUERY, queryObject);
            result.add(SEARCH_API_FILTER, filterObject);
            return result;
        }

        private JsonObject translateMatch(Query query) {
            if (query == null) return null;

            String field = query.getField();
            Object value = query.getParam(Query.Parameter.VALUE.name());

            JsonObject result = new JsonObject();
            JsonObject subObject= new JsonObject();
            subObject.addProperty(field, (String) value);
            result.add(SEARCH_API_MATCH, subObject);
            return result;
        }

        private JsonObject translateMatchAll(Query query) {
            if (query == null) return null;

            JsonObject result = new JsonObject();
            result.add(SEARCH_API_MATCH_ALL, new JsonObject());
            return result;
        }

        private JsonObject translateBool(Query query) {
            if (query == null) return null;

            JsonObject result = new JsonObject();

            JsonElement mustObject = null;
            JsonElement mustNotObject = null;
            JsonElement shouldObject = null;
            try {
                mustObject = translateGsonQueries((List<Query>) query.getParam(Query.Parameter.MUST.name()));
            } catch (ClassCastException e) {
                mustObject = translate((Query) query.getParam(Query.Parameter.MUST.name()));
            }
            try {
                mustNotObject = translateGsonQueries((List<Query>) query.getParam(Query.Parameter.MUST_NOT.name()));
            } catch (ClassCastException e) {
                mustNotObject = translate((Query) query.getParam(Query.Parameter.MUST.name()));
            }
            try {
                shouldObject = translateGsonQueries((List<Query>) query.getParam(Query.Parameter.SHOULD.name()));
            } catch (ClassCastException e) {
                shouldObject = translate((Query) query.getParam(Query.Parameter.MUST.name()));
            }

            JsonObject bool = new JsonObject();
            if (mustObject != null) bool.add(SEARCH_API_MUST, mustObject);
            if (mustNotObject != null) bool.add(SEARCH_API_MUST_NOT, mustNotObject);
            if (shouldObject!= null) bool.add(SEARCH_API_SHOULD, shouldObject);
            result.add(SEARCH_API_BOOL, bool);
            return result;
        }

        private JsonElement translateGsonQueries(List<Query> queries) {
            JsonElement result = null;
            if (queries != null && !queries.isEmpty()) {
                result = new JsonObject();
                List<JsonObject> jsonObjects = translateQueries(queries);
                if (jsonObjects.size() == 1) {
                    result = jsonObjects.get(0);
                } else if (jsonObjects.size() > 1) {
                    JsonArray mustArray = new JsonArray();
                    for (JsonObject jsonObject : jsonObjects) {
                        mustArray.add(jsonObject);
                    }
                    result = mustArray;
                }
            }
            return result;
        }

        private List<JsonObject> translateQueries(List<Query> queries) {
            List<JsonObject> result = new LinkedList<JsonObject>();
            for (Query subQuery : queries) {
                JsonObject subObject = translate(subQuery);
                result.add(subObject);
            }
            return result;
        }
    }
    
    /*
     *********************************************************************
       * Helper methods.
     *********************************************************************
     */

    protected JestClient buildClient() throws IllegalArgumentException{
        return  client = buildNewClient();
    }

    protected JestClient buildNewClient() throws IllegalArgumentException{
        if (serverURL == null || serverURL.trim().length() == 0) throw new IllegalArgumentException("Parameter serverURL is missing.");
        if (clusterName == null || clusterName.trim().length() == 0) throw new IllegalArgumentException("Parameter clusterName is missing.");

        // TODO: use clusterName.
        JestClientFactory factory = new JestClientFactory();
        factory.setHttpClientConfig(new HttpClientConfig
                .Builder(serverURL)
                .multiThreaded(true)
                .connTimeout(timeout)
                .build());
        
        return factory.getObject();
    }
    
}