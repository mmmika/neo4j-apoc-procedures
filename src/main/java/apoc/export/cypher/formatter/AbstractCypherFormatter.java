package apoc.export.cypher.formatter;

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.helpers.collection.Iterables;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static apoc.export.cypher.formatter.CypherFormatterUtils.Q_UNIQUE_ID_LABEL;
import static apoc.export.cypher.formatter.CypherFormatterUtils.UNIQUE_ID_PROP;
import static apoc.export.cypher.formatter.CypherFormatterUtils.quote;

/**
 * @author AgileLARUS
 *
 * @since 16-06-2017
 */
abstract class AbstractCypherFormatter implements CypherFormatter {

	private static final String STATEMENT_CONSTRAINTS = "CREATE CONSTRAINT ON (node:%s) ASSERT (%s) %s;";

	@Override
	public String statementForCleanUp(int batchSize) {
		return "MATCH (n:" + Q_UNIQUE_ID_LABEL + ") " +
				" WITH n LIMIT " + batchSize +
				" REMOVE n:" + Q_UNIQUE_ID_LABEL + " REMOVE n." + quote(UNIQUE_ID_PROP) + ";";
	}

	@Override
	public String statementForIndex(String label, Iterable<String> keys) {
		return "CREATE INDEX ON :" + CypherFormatterUtils.quote(label) + "(" + CypherFormatterUtils.quote(keys) + ");";
	}

	@Override
	public String statementForConstraint(String label, Iterable<String> keys) {

		String keysString = StreamSupport.stream(keys.spliterator(), false)
				.map(key -> "node." + CypherFormatterUtils.quote(key))
				.collect(Collectors.joining(", "));

		return  String.format(STATEMENT_CONSTRAINTS, CypherFormatterUtils.quote(label), keysString, Iterables.count(keys) > 1 ? "IS NODE KEY" : "IS UNIQUE");
	}

	protected String mergeStatementForNode(CypherFormat cypherFormat, Node node, Map<String, Set<String>> uniqueConstraints, Set<String> indexedProperties, Set<String> indexNames) {
		StringBuilder result = new StringBuilder(1000);
		result.append("MERGE ");
		result.append(CypherFormatterUtils.formatNodeLookup("n", node, uniqueConstraints, indexNames));
		if (node.getPropertyKeys().iterator().hasNext()) {
			String notUniqueProperties = CypherFormatterUtils.formatNotUniqueProperties("n", node, uniqueConstraints, indexedProperties, false);
			String notUniqueLabels = CypherFormatterUtils.formatNotUniqueLabels("n", node, uniqueConstraints);
			if (!"".equals(notUniqueProperties) || !"".equals(notUniqueLabels)) {
				result.append(cypherFormat.equals(CypherFormat.ADD_STRUCTURE) ? " ON CREATE SET " : " SET ");
				result.append(notUniqueProperties);
				result.append(!"".equals(notUniqueProperties) && !"".equals(notUniqueLabels) ? ", " : "");
				result.append(notUniqueLabels);
			}
		}
		result.append(";");
		return result.toString();
	}

	public String mergeStatementForRelationship(CypherFormat cypherFormat, Relationship relationship, Map<String, Set<String>> uniqueConstraints, Set<String> indexedProperties) {
		StringBuilder result = new StringBuilder(1000);
		result.append("MATCH ");
		result.append(CypherFormatterUtils.formatNodeLookup("n1", relationship.getStartNode(), uniqueConstraints, indexedProperties));
		result.append(", ");
		result.append(CypherFormatterUtils.formatNodeLookup("n2", relationship.getEndNode(), uniqueConstraints, indexedProperties));
		result.append(" MERGE (n1)-[r:" + CypherFormatterUtils.quote(relationship.getType().name()) + "]->(n2)");
		if (relationship.getPropertyKeys().iterator().hasNext()) {
			result.append(cypherFormat.equals(CypherFormat.UPDATE_STRUCTURE) ? " ON CREATE SET " : " SET ");
			result.append(CypherFormatterUtils.formatRelationshipProperties("r", relationship, false));
		}
		result.append(";");
		return result.toString();
	}
}

