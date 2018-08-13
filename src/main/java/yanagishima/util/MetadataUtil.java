package yanagishima.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.fluent.Request;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

public class MetadataUtil {

    public static void setMetadata(String metadataServiceUrl, HashMap<String, Object> retVal, String schema, String table, List<List<String>> records) {
        try {
            String json = Request.Get(String.format("%s/%s/%s", metadataServiceUrl, schema, table)).execute().returnContent().asString(UTF_8);
            ObjectMapper mapper = new ObjectMapper();
            Map map = mapper.readValue(json, Map.class);
            List<Map> columns = (List) map.get("columns");
            List<List<String>> newRecordList = new ArrayList<List<String>>();
            for (int i = 0; i < records.size(); i++) {
                List<String> newColumnList = new ArrayList<>();
                for (String column : records.get(i)) {
                    newColumnList.add(column);
                }
                if(i < columns.size()) {
                    Object n = columns.get(i).get("note");
                    if (n == null) {
                        newColumnList.add(null);
                    } else {
                        newColumnList.add((String) ((Map) n).get("note"));
                    }
                }
                newRecordList.add(newColumnList);
            }
            retVal.put("results", newRecordList);
            if (map.get("note") != null) {
                retVal.put("note", ((Map) map.get("note")).get("note"));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
