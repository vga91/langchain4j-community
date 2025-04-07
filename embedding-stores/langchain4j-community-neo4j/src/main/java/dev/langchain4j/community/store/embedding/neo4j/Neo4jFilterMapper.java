package dev.langchain4j.community.store.embedding.neo4j;

import static org.neo4j.cypherdsl.core.Cypher.asExpression;
import static org.neo4j.cypherdsl.core.Cypher.literalOf;
import static org.neo4j.cypherdsl.core.Cypher.mapOf;
import static org.neo4j.cypherdsl.core.Cypher.not;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
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
import java.time.OffsetDateTime;
import java.util.Map;
import org.neo4j.cypherdsl.core.Condition;
import org.neo4j.cypherdsl.core.Cypher;
import org.neo4j.cypherdsl.core.Expression;
import org.neo4j.cypherdsl.core.FunctionInvocation;
import org.neo4j.cypherdsl.core.Node;
import org.neo4j.driver.internal.value.ListValue;
import org.neo4j.driver.internal.value.PointValue;

public class Neo4jFilterMapper {

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static FunctionInvocation convertToPoint(PointValue value1) {
        try {
            String s = OBJECT_MAPPER.writeValueAsString(value1.asObject());
            Map<String, Object> map = new JsonMapper().readValue(s, Map.class);
            return Cypher.point(asExpression(map));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Expression toCypherLiteral(Object value) {
        if (value instanceof OffsetDateTime) {
            return Cypher.datetime(literalOf(value.toString()));
        }
        if (value instanceof final PointValue pointValue) {
            return convertToPoint(pointValue);
        }
        if (value instanceof Map) {
            return mapOf(value);
        }
        if (value instanceof ListValue listValue) {
            return literalOf(listValue.asList());
        }

        // Other types
        return literalOf(value);
    }

    public static final String UNSUPPORTED_FILTER_TYPE_ERROR = "Unsupported filter type: ";

    private final Node node;

    public Neo4jFilterMapper(final Node node) {
        this.node = node;
    }

    // TODO - should return `Condition` ??
    public Condition getStringMapping(Filter filter) {
        if (filter instanceof IsEqualTo item) {
            final Expression cypherLiteral = toCypherLiteral(item.key());
            final Expression cypherLiteral1 = toCypherLiteral(item.comparisonValue());
            return node.property(cypherLiteral).eq(cypherLiteral1);
        } else if (filter instanceof IsNotEqualTo item) {
            final Expression cypherLiteral = toCypherLiteral(item.key());
            final Expression cypherLiteral1 = toCypherLiteral(item.comparisonValue());
            return node.property(cypherLiteral).isNotEqualTo(cypherLiteral1);
        } else if (filter instanceof IsGreaterThan item) {
            final Expression cypherLiteral = toCypherLiteral(item.key());
            final Expression cypherLiteral1 = toCypherLiteral(item.comparisonValue());
            return node.property(cypherLiteral).gt(cypherLiteral1);
        } else if (filter instanceof IsGreaterThanOrEqualTo item) {
            final Expression cypherLiteral = toCypherLiteral(item.key());
            final Expression cypherLiteral1 = toCypherLiteral(item.comparisonValue());
            return node.property(cypherLiteral).gte(cypherLiteral1);
        } else if (filter instanceof IsLessThan item) {
            final Expression cypherLiteral = toCypherLiteral(item.key());
            final Expression cypherLiteral1 = toCypherLiteral(item.comparisonValue());
            return node.property(cypherLiteral).lt(cypherLiteral1);
        } else if (filter instanceof IsLessThanOrEqualTo item) {
            final Expression cypherLiteral = toCypherLiteral(item.key());
            final Expression cypherLiteral1 = toCypherLiteral(item.comparisonValue());
            return node.property(cypherLiteral).lte(cypherLiteral1);
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

    public Condition mapIn(IsIn filter) {
        final Expression cypherLiteral = toCypherLiteral(filter.key());
        final Expression cypherLiteral1 = toCypherLiteral(filter.comparisonValues());
        return Cypher.includesAny(node.property(cypherLiteral), cypherLiteral1);
    }

    public Condition mapNotIn(IsNotIn filter) {
        final Expression cypherLiteral = toCypherLiteral(filter.key());
        final Expression cypherLiteral1 = toCypherLiteral(filter.comparisonValues());
        final Condition condition1 = Cypher.includesAny(node.property(cypherLiteral), cypherLiteral1);
        return not(condition1);
    }

    private Condition mapAnd(And filter) {
        final Condition left = getStringMapping(filter.left());
        final Condition right = getStringMapping(filter.right());
        return left.and(right);
    }

    private Condition mapOr(Or filter) {
        final Condition left = getStringMapping(filter.left());
        final Condition right = getStringMapping(filter.right());
        return left.or(right);
    }

    private Condition mapNot(Not filter) {
        final Condition expression = getStringMapping(filter.expression());
        return not(expression);
    }
}
