package dev.langchain4j.community.store.embedding.neo4j;

import static org.neo4j.cypherdsl.core.Cypher.literalOf;
import static org.neo4j.cypherdsl.core.Cypher.mapOf;
import static org.neo4j.cypherdsl.support.schema_name.SchemaNames.sanitize;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThan;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsIn;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThan;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotIn;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;
import org.neo4j.cypherdsl.core.Cypher;
import org.neo4j.cypherdsl.core.Expression;
import org.neo4j.cypherdsl.core.MapExpression;
import org.neo4j.cypherdsl.core.Node;
import org.neo4j.cypherdsl.core.StatementBuilder;
import org.neo4j.driver.internal.value.PointValue;
import org.neo4j.driver.types.Point;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.AbstractMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class Neo4jFilterMapper {

    // TODO - use this
    public static Expression toCypherLiteral(Object value) {
        if (value instanceof Integer || value instanceof Long || value instanceof Boolean || value instanceof String) {
            return literalOf(value); // Basic types
        }
        else if (value instanceof LocalDate) {
            return Cypher.date(literalOf(value.toString())); // date('2024-03-30')
        }
        else if (value instanceof LocalTime) {
            return Cypher.localtime(literalOf(value.toString())); // localtime('12:30:00')
        }
        else if (value instanceof OffsetTime) {
            return Cypher.time(literalOf(value.toString())); // time('12:30:00+02:00')
        }
        else if (value instanceof LocalDateTime) {
            return Cypher.localdatetime(literalOf(value.toString())); // localdatetime('2024-03-30T12:30:00')
        }
        else if (value instanceof OffsetDateTime) {
            return Cypher.datetime(literalOf(value.toString())); // datetime('2024-03-30T12:30:00+02:00')
        }
        else if (value instanceof final PointValue value1) {
            return Cypher.point(mapOf(value1.asMap())); // point({longitude: 13.4, latitude: 52.5})
        }
        else if (value instanceof Map) {
            return mapOf(value); // Convert Java Map to Cypher Map
        }

        // Other types
        return literalOf(value); 
        //throw new IllegalArgumentException("Unsupported type: " + value.getClass());
    }
    
    public static final String UNSUPPORTED_FILTER_TYPE_ERROR = "Unsupported filter type: ";

    public static class IncrementalKeyMap {
        private final Map<String, Object> map = new ConcurrentHashMap<>();

        private final AtomicInteger integer = new AtomicInteger();

        public String put(Object value) {
            String key = "param_" + integer.getAndIncrement();
            map.put(key, value);
            return key;
        }

        public Map<String, Object> getMap() {
            return map;
        }
    }

    public Neo4jFilterMapper() {}

//    static {
//        // TODO..
//        
//        var m = named;
//        
//    }

    final static Node named = Cypher.node("Movie").named("m");
    final static StatementBuilder.OngoingReadingWithoutWhere matchBuilder = Cypher.match(named);
    
    final IncrementalKeyMap map = new IncrementalKeyMap();

    AbstractMap.SimpleEntry<String, Map<String, Object>> map(Filter filter) {
        final String stringMapPair = getStringMapping(filter);
        return new AbstractMap.SimpleEntry<>(stringMapPair, map.getMap());
    }

    
    // TODO - should return `Condition` ??
    private String getStringMapping(Filter filter) {
        if (filter instanceof IsEqualTo item) {
            return getOperation(item.key(), "=", item.comparisonValue());
        } else if (filter instanceof IsNotEqualTo item) {
            return getOperation(item.key(), "<>", item.comparisonValue());
        } else if (filter instanceof IsGreaterThan item) {
            return getOperation(item.key(), ">", item.comparisonValue());
        } else if (filter instanceof IsGreaterThanOrEqualTo item) {
            return getOperation(item.key(), ">=", item.comparisonValue());
        } else if (filter instanceof IsLessThan item) {
            return getOperation(item.key(), "<", item.comparisonValue());
        } else if (filter instanceof IsLessThanOrEqualTo item) {
            return getOperation(item.key(), "<=", item.comparisonValue());
        } else if (filter instanceof IsIn item) {
            return mapIn(item);
        } else if (filter instanceof IsNotIn item) {
            return mapNotIn(item);
        } else if (filter instanceof And item) {
            return mapAnd(item);
        } else if (filter instanceof Not item) {
            return mapNot(item);
        } else if (filter instanceof Or item) {
            return mapOr(item);
        } else {
            throw new UnsupportedOperationException(
                    UNSUPPORTED_FILTER_TYPE_ERROR + filter.getClass().getName());
        }
    }
    
    /*
    var m = Cypher.node("Movie").named("m");
var statement = Cypher.match(m)
        .where(m.property("a").isEqualTo(Cypher.literalOf("1")))
        .returning(m)
        .build();

statement.getCypher()

MATCH (m:`Movie`) WHERE m.a = '1' RETURN m
     */

    private String getOperation(String key, String operator, Object value) {
        final MapExpression a = Cypher.asExpression(Map.of("a", "1"));

        // put ($param_N, <value>) entry map
        final String param = map.put(value);

        String sanitizedKey = sanitize(key).orElseThrow(() -> {
            String invalidSanitizeValue = String.format(
                    "The key %s, to assign to the operator %s and value %s, cannot be safely quoted",
                    key, operator, value);
            return new RuntimeException(invalidSanitizeValue);
        });

        return String.format("n.%s %s $%s", sanitizedKey, operator, param);
    }

    public String mapIn(IsIn filter) {
        return getOperation(filter.key(), "IN", filter.comparisonValues());
    }

    public String mapNotIn(IsNotIn filter) {
        final String inOperation = getOperation(filter.key(), "IN", filter.comparisonValues());
        return String.format("NOT (%s)", inOperation);
    }

    private String mapAnd(And filter) {
        return String.format("(%s) AND (%s)", getStringMapping(filter.left()), getStringMapping(filter.right()));
    }

    private String mapOr(Or filter) {
        return String.format("(%s) OR (%s)", getStringMapping(filter.left()), getStringMapping(filter.right()));
    }

    private String mapNot(Not filter) {
        return String.format("NOT (%s)", getStringMapping(filter.expression()));
    }
}
