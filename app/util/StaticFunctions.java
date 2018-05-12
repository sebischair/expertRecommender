package util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bson.Document;
import play.libs.Json;
import play.mvc.Result;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.Collections.reverseOrder;

/**
 * Created by mahabaleshwar on 8/29/2016.
 */
public class StaticFunctions {
    public static final String DESCRIPTION = "description";
    public static final String NAME = "name";

    public static final String ASSIGNEE = "assignee";
    public static final String CONCEPTS = "concepts";
    public static final String SUMMARY = "summary";
    public static final String VALUES = "values";
    public static final String STATUS = "fields.status.name";
    public static final String RESOLUTION_DATE = "resolved";
    public static final String CREATED_DATE = "created";
    public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    public static final String PRIORITY = "priority";

    public static final Set<String> STATUS_FINISHED = new HashSet<>();

    static {
        STATUS_FINISHED.add("Resolved");
        STATUS_FINISHED.add("Closed");
    }

    public static final String PERSONNAME = "personName";

    public static Result jsonResult(Result httpResponse) {
        return httpResponse.as("application/json; charset=utf-8");
    }

    public static ObjectNode errorAsJson(Throwable error) {
        ObjectNode result = Json.newObject();
        result.put("status", "error");
        result.put("message", error.toString());
        return result;
    }

    public static ArrayNode sortJsonArray(ArrayNode arrayNode) {
        ArrayNode sortedJsonArray = Json.newArray();

        List<JsonNode> jsonValues = new ArrayList<>();
        for (int i = 0; i < arrayNode.size(); i++) {
            jsonValues.add(arrayNode.get(i));
        }

        jsonValues.sort(new Comparator<JsonNode>() {
            private static final String KEY_NAME = "averageScore";

            @Override
            public int compare(JsonNode a, JsonNode b) {
                try {
                    if (a.get(KEY_NAME) != null && b.get(KEY_NAME) != null) {
                        double valA = a.get(KEY_NAME).asDouble();
                        double valB = b.get(KEY_NAME).asDouble();
                        if (valB == valA) {
                            return 0;
                        } else if (valB > valA) {
                            return 1;
                        } else {
                            return -1;
                        }
                    } else if (a.get(KEY_NAME) == null) {
                        return 1;
                    } else {
                        return -1;
                    }
                } catch (Exception e) {
                    return -1;
                }
            }
        });

        for (int i = 0; i < arrayNode.size(); i++) {
            sortedJsonArray.add(jsonValues.get(i));
        }
        return sortedJsonArray;
    }

    public static boolean containsStringValue(String key, String value, ArrayNode ja) {
        for (JsonNode jo : ja) {
            if (jo.get(key).asText("").equals(value.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    public static JsonNode getJSONObject(String key, String value, ArrayNode ja) {
        for (JsonNode jo : ja) {
            if (jo.get(key).asText("").equals(value.toLowerCase())) {
                return jo;
            }
        }
        return null;
    }

    public static void updateConceptArray(String conceptName, JsonNode conceptArray, double factor) {
        if (conceptArray.size() == 0) {
            addConceptToConceptArray(conceptName, conceptArray, factor);
        } else {
            boolean isUpdated = false;
            for (JsonNode conceptObject : conceptArray) {
                if (conceptObject.get("conceptName").asText("").equals(conceptName.toLowerCase())) {
                    int value = conceptObject.get("value").asInt(0);
                    ((ObjectNode) conceptObject).put("value", value + factor);
                    isUpdated = true;
                }
            }
            if (!isUpdated) {
                addConceptToConceptArray(conceptName, conceptArray, factor);
            }
        }
    }

    public static void updateConceptArray(String conceptName, JsonNode conceptArray) {
        updateConceptArray(conceptName, conceptArray, 1);
    }

    private static void addConceptToConceptArray(String conceptName, JsonNode conceptArray, double factor) {
        ObjectNode jo = Json.newObject();
        jo.put("conceptName", conceptName);
        jo.put("value", factor);
        ((ArrayNode) conceptArray).add(jo);
    }


    public static List<String> getItemsToRemove(ArrayNode ja) {
        List<String> itemsToRemove = new ArrayList<>();
        itemsToRemove.add("unassigned");
        ja.forEach(jo -> {
            if (jo.get(CONCEPTS).size() == 0) {
                itemsToRemove.add(jo.get("personName").asText(""));
            }
        });
        return itemsToRemove;
    }

    public static void removeItemsFromJSONArray(ArrayNode ja, List<String> itemsToRemove) {
        for (String pName : itemsToRemove) {
            for (int i = 0; i < ja.size(); i++) {
                JsonNode jo = ja.get(i);
                if (jo.get("personName").asText("").equalsIgnoreCase(pName)) {
                    ja.remove(i);
                    break;
                }
            }
        }
    }

    public static String truncate(String text) {
        if (text.length() > 50)
            return text.substring(0, 50) + " ...";
        else
            return text;
    }

    public static LinkedHashMap<String, Double> sortByValues(LinkedHashMap<String, Double> mapToSort) {
        return mapToSort.entrySet().stream().
                sorted(reverseOrder(Map.Entry.comparingByValue())).
                collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (e1, e2) -> e1, LinkedHashMap::new));
    }

    public static void writeToFile(ObjectNode obj, String filepath) {
        try (PrintWriter out = new PrintWriter(new FileOutputStream(new File(filepath), true))) {
            out.println(obj);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static int getConceptValue(String concept, String s) {
        s = s.replaceAll("\\(", "").replaceAll("\\)", "");
        int i = 0;
        Pattern p = Pattern.compile(concept.toLowerCase());
        Matcher m = p.matcher(s.toLowerCase());
        while (m.find()) {
            i++;
        }
        return i;
    }
}