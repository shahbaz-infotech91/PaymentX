package com.paymentx.reporting.service.exporter;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * ====================================================================
 * ENGLISH:
 * Converts a report's JSON result data into a flat list of (section,
 * field, value) rows suitable for tabular rendering - shared by all 4
 * exporters, directly satisfying the explicit "No duplicate logic"
 * requirement (without this, each exporter would need its own
 * JSON-tree-walking code).
 *
 * HINGLISH:
 * Ek report ke JSON result data ko (section, field, value) rows ki flat
 * list me convert karta hai jo tabular rendering ke liye theek hai -
 * sab 4 exporters ise share karte hain, explicit "No duplicate logic"
 * requirement ko directly satisfy karte hue.
 * ====================================================================
 */
@Component
public class ReportDataFlattener {

    public record Row(String section, String field, String value) {}

    public List<Row> flatten(JsonNode root) {
        List<Row> rows = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            flattenField(entry.getKey(), entry.getValue(), rows);
        }
        return rows;
    }

    private void flattenField(String section, JsonNode value, List<Row> rows) {
        if (value.isArray()) {
            for (JsonNode element : value) {
                if (element.isObject()) {
                    StringBuilder line = new StringBuilder();
                    Iterator<Map.Entry<String, JsonNode>> elementFields = element.fields();
                    while (elementFields.hasNext()) {
                        Map.Entry<String, JsonNode> f = elementFields.next();
                        if (!line.isEmpty()) {
                            line.append(", ");
                        }
                        line.append(f.getKey()).append('=').append(f.getValue().asText());
                    }
                    rows.add(new Row(section, "", line.toString()));
                } else {
                    rows.add(new Row(section, "", element.asText()));
                }
            }
        } else if (value.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> nestedFields = value.fields();
            while (nestedFields.hasNext()) {
                Map.Entry<String, JsonNode> nested = nestedFields.next();
                rows.add(new Row(section, nested.getKey(), nested.getValue().asText()));
            }
        } else {
            rows.add(new Row(section, "", value.asText()));
        }
    }
}
