/*
 * GRAKN.AI - THE KNOWLEDGE GRAPH
 * Copyright (C) 2018 Grakn Labs Ltd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package grakn.core.graql.reasoner.query;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import grakn.core.graql.answer.ConceptMap;
import grakn.core.graql.concept.Concept;
import grakn.core.graql.concept.EntityType;
import grakn.core.graql.concept.Label;
import grakn.core.graql.concept.RelationType;
import grakn.core.graql.concept.Role;
import grakn.core.graql.concept.SchemaConcept;
import grakn.core.graql.concept.Thing;
import grakn.core.graql.query.GetQuery;
import grakn.core.graql.query.Graql;
import grakn.core.graql.query.pattern.Conjunction;
import grakn.core.graql.query.pattern.Disjunction;
import grakn.core.graql.query.pattern.Pattern;
import grakn.core.graql.query.pattern.Statement;
import grakn.core.graql.query.pattern.property.IsaProperty;
import grakn.core.rule.GraknTestServer;
import grakn.core.server.Transaction;
import grakn.core.server.session.SessionImpl;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;

import static grakn.core.util.GraqlTestUtil.assertCollectionsEqual;
import static grakn.core.util.GraqlTestUtil.loadFromFileAndCommit;
import static java.util.stream.Collectors.toSet;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

public class NegationIT {

    private static String resourcePath = "test-integration/graql/reasoner/stubs/";

    @ClassRule
    public static final GraknTestServer server = new GraknTestServer();

    private static SessionImpl negationSession;

    @BeforeClass
    public static void loadContext(){
        negationSession = server.sessionWithNewKeyspace();
        loadFromFileAndCommit(resourcePath,"negation.gql", negationSession);
    }

    @AfterClass
    public static void closeSession(){
        negationSession.close();
    }

    @Test
    public void whenNegatingStatementsWithMultipleProperties_MultiPropertyTreatedAsConjunction(){
        Pattern pattern = Pattern.parse(
                    "{" +
                            "$x has attribute $r;" +
                            "NOT $x isa type" +
                            " has resource-string 'value'" +
                            " has derived-resource-string 'otherValue';" +
                            "}"
        );

        Pattern equivalentPattern = Pattern.or(
                Pattern.parse(
                            "{" +
                                    "$x has attribute $r;" +
                                    "NOT $x isa type;" +
                                    "}"),
                Pattern.parse(
                            "{" +
                                    "$x has attribute $r;" +
                                    "NOT $x has resource-string 'value';" +
                                    "}"),
                Pattern.parse(
                            "{" +
                                    "$x has attribute $r;" +
                                    "NOT $x has derived-resource-string 'otherValue';" +
                                    "}")
            );

        assertEquals(equivalentPattern.getDisjunctiveNormalForm(), pattern.getDisjunctiveNormalForm());
    }

    @Test
    public void whenNegatingStatementTwice_gettingOriginalStatement(){
        Pattern pattern = Pattern.parse("{" + 
                "$x has resource-string 'value';" + 
                "NOT {NOT ($x, $y)};" + 
                "$y has derived-resource-string 'anotherValue';" + 
                "}"
        );
        Pattern equivalentPattern = Pattern.parse("{" + 
                "$x has resource-string 'value';" + 
                "($x, $y);" + 
                "$y has derived-resource-string 'anotherValue';" + 
                "}"
        );
        assertEquals(pattern.getDisjunctiveNormalForm(), equivalentPattern.getDisjunctiveNormalForm());
    }

    @Test
    public void whenNegatedPatternContainsNegatedStatements_flattenedPatternIsCorrect(){
        Pattern pattern = Pattern.parse(
                "NOT {" + 
                        "{" + 
                            "$x has resource-string 'value';" + 
                            "NOT ($x, $y);" + 
                            "$y has derived-resource-string 'anotherValue';" + 
                        "}" + 
                        "}"
        );

        Pattern equivalentPattern = Pattern.or(
                Pattern.not(Pattern.parse("$x has resource-string 'value';")),
                Pattern.parse("($x, $y);"),
                Pattern.not(Pattern.parse("$y has derived-resource-string 'anotherValue';"))
        );

        assertEquals(pattern.getDisjunctiveNormalForm(), equivalentPattern.getDisjunctiveNormalForm());
    }

    @Test
    public void whenNegatingPatternTwice_gettingOriginalPattern(){
        String basePattern = 
                "$x has resource-string 'value';" + 
                "($x, $y);" + 
                "$y has derived-resource-string 'anotherValue';";

        Pattern pattern = Pattern.parse(
                "NOT " + 
                            "{" + 
                                "NOT {" +
                                    "{" +
                                         basePattern +
                                    "}" +
                                "}" +
                            "};"
        );
        Pattern equivalentPattern = Pattern.parse("{" + basePattern + "}");
        assertEquals(pattern.getDisjunctiveNormalForm(), equivalentPattern.getDisjunctiveNormalForm());
    }

    @Test
    public void whenNegatedPatternIsARelation_patternFlattenedCorrectly(){
        Pattern pattern = Pattern.parse(
                "{" +
                            "(someRole: $x, otherRole: $y) isa binary;" +
                            "NOT (someRole: $y, otherRole: $q) isa binary;" +
                            "(someRole: $y, otherRole: $z) isa binary;" +
                            "}"
        );

        Disjunction<Conjunction<Statement>> dnf = pattern.getDisjunctiveNormalForm();
        assertEquals(1, dnf.getPatterns().size());
        assertTrue(
                Iterables.getOnlyElement(dnf.getPatterns())
                        .getPatterns().stream()
                        .allMatch(p -> p.asStatement().getProperties(IsaProperty.class).findFirst().isPresent())
        );
    }

    @Test
    public void whenNegatedPatternIsAMultiPropertyStatement_statementTreatedAsConjunction(){
        Pattern pattern = Pattern.parse(
                    "{" +
                            "NOT $x isa type has attribute 'someValue' has resource-string 'value';" +
                            "(someRole: $x, otherRole: $y) isa binary;" +
                            "(someRole: $y, otherRole: $z) isa binary;" +
                            "}"
        );

        Pattern equivalentPattern = Pattern.parse(
                    "{" +
                            "NOT {" +
                                "{" +
                                    "$x isa type;" +
                                    "$x has attribute 'someValue';" +
                                    "$x has resource-string 'value';" +
                                "}" +
                            "};" +
                            "(someRole: $x, otherRole: $y) isa binary;" +
                            "(someRole: $y, otherRole: $z) isa binary;" +
                            "}"
        );
        assertEquals(equivalentPattern.getDisjunctiveNormalForm(), pattern.getDisjunctiveNormalForm());
    }

    @Test
    public void whenNegatedPatternIsAConjunction_patternFlattenedCorrectly(){
        Pattern pattern = Pattern.parse(
                    "{" +
                            "$x isa type;" +
                            "NOT {" +
                                "{" +
                                    "$x has resource-string 'value';" +
                                    "($x, $y);" +
                                    "$y has derived-resource-string 'anotherValue';" +
                                "}" +
                            "};" +
                            "}"
        );
        Pattern equivalentPattern = Pattern.parse("{" +
                    "{$x isa type;" +
                    "NOT $x has resource-string 'value';}" +
                    " or " +
                    "{$x isa type;" +
                    "NOT ($x, $y);}" +
                    " or " +
                    "{$x isa type;" +
                    "NOT $y has derived-resource-string 'anotherValue';};" +
                    "}"
        );
        assertEquals(pattern.getDisjunctiveNormalForm(), equivalentPattern.getDisjunctiveNormalForm());
    }

    @Test
    public void whenNegatedPatternIsADisjunction_patternFlattenedCorrectly(){
        Pattern pattern = Pattern.parse(
                    "{" +
                            "$x isa type;" +
                            "NOT {" +
                                "{$x has resource-string 'value';}" +
                                " or " +
                                "{$x has derived-resource-string 'anotherValue';}" +
                            "};" +
                            "}"
        );
        Pattern equivalentPattern = Pattern.parse(
                    "{" +
                    "$x isa type;" +
                    "NOT $x has resource-string 'value';" +
                    "NOT $x has derived-resource-string 'anotherValue';" +
                    "}"
        );
        assertEquals(pattern.getDisjunctiveNormalForm(), equivalentPattern.getDisjunctiveNormalForm());
    }

    @Test
    public void whenNegatedPatternIsNested_patternFlattenedCorrectly(){
        Pattern pattern = Pattern.parse(
                    "{" +
                            "$x isa type;" +
                            "NOT {" +
                                "{" +
                                    "$x has resource-string 'value';" +
                                    "($x, $y);" +
                                    "{$y has resource-long 1;} or {$y has resource-long 0;};" +
                                "}" +
                            "};" +
                            "}"
        );
        Pattern equivalentPattern = Pattern.parse("{" +
                    "{$x isa type;" +
                    "NOT $x has resource-string 'value';}" +
                    " or " +
                    "{$x isa type;" +
                    "NOT ($x, $y);}" +
                    " or " +
                    "{$x isa type;" +
                    "NOT $y has resource-long 1;" +
                    "NOT $y has resource-long 0;};" +
                    "}"
        );
        assertEquals(pattern.getDisjunctiveNormalForm(), equivalentPattern.getDisjunctiveNormalForm());
    }

    @Test (expected = IllegalStateException.class)
    public void whenNegatingSinglePattern_exceptionIsThrown () {
        try(Transaction tx = negationSession.transaction(Transaction.Type.WRITE)) {
            String specificValue = "value";
            String attributeTypeLabel = "resource-string";

            List<ConceptMap> answers = tx.execute(Graql.<GetQuery>parse(
                    "match " +
                            "NOT $x has " + attributeTypeLabel + " '" + specificValue + "';" +
                            "get;"
            ));
        }

    }

    @Test
    public void conjunctionOfRelations_filteringSpecificRolePlayerType(){
        try(Transaction tx = negationSession.transaction(Transaction.Type.WRITE)) {
            String unwantedLabel = "anotherType";
            EntityType unwantedType = tx.getEntityType(unwantedLabel);

            List<ConceptMap> answersWithoutSpecificRoleplayerType = tx.execute(Graql.<GetQuery>parse(
                    "match " +
                            "(someRole: $x, otherRole: $y) isa binary;" +
                            "NOT $q isa " + unwantedLabel + ";" +
                            "(someRole: $y, otherRole: $q) isa binary;" +
                            "(someRole: $y, otherRole: $z) isa binary;" +
                            "get;"
            ));
            
            List<ConceptMap> fullAnswers = tx.execute(Graql.<GetQuery>parse(
                    "match " +
                            "(someRole: $x, otherRole: $y) isa binary;" +
                            "(someRole: $y, otherRole: $q) isa binary;" +
                            "(someRole: $y, otherRole: $z) isa binary;" +
                            "get;"
            ));

            Set<ConceptMap> expectedAnswers = fullAnswers.stream().filter(ans -> !ans.get("q").asThing().type().equals(unwantedType)).collect(toSet());

            assertCollectionsEqual(
                    expectedAnswers,
                    answersWithoutSpecificRoleplayerType
            );
        }
    }

    @Test
    public void conjunctionOfRelations_filteringSpecificUnresolvableConnection(){
        try(Transaction tx = negationSession.transaction(Transaction.Type.WRITE)) {
            String unwantedLabel = "anotherType";
            String connection = "binary";

            List<ConceptMap> answersWithoutSpecificConnection = tx.execute(Graql.<GetQuery>parse(
                    "match " +
                            "(someRole: $x, otherRole: $y) isa binary;" +
                            "$q isa " + unwantedLabel + ";" +
                            "NOT (someRole: $y, otherRole: $q) isa " + connection + ";" +
                            "(someRole: $y, otherRole: $z) isa binary;" +
                            "get;"
            ));

            List<ConceptMap> fullAnswers = tx.execute(Graql.<GetQuery>parse(
                    "match " +
                            "(someRole: $x, otherRole: $y) isa binary;" +
                            "$q isa " + unwantedLabel + ";" +
                            "(someRole: $y, otherRole: $z) isa binary;" +
                            "get;"
            ));

            Set<ConceptMap> expectedAnswers = fullAnswers.stream()
                    .filter(ans -> !thingsRelated(
                            ImmutableMap.of(
                                    ans.get("y").asThing(), tx.getRole("someRole"),
                                    ans.get("q").asThing(), tx.getRole("otherRole")),
                            Label.of(connection),
                            tx)
                    ).collect(toSet());

            assertCollectionsEqual(
                    expectedAnswers,
                    answersWithoutSpecificConnection
            );
        }
    }

    @Test
    public void conjunctionOfRelations_filteringSpecificResolvableConnection(){
        try(Transaction tx = negationSession.transaction(Transaction.Type.WRITE)) {
            String unwantedLabel = "anotherType";
            String connection = "derived-binary";

            List<ConceptMap> answersWithoutSpecificConnection = tx.execute(Graql.<GetQuery>parse(
                    "match " +
                            "(someRole: $x, otherRole: $y) isa binary;" +
                            "$q isa " + unwantedLabel + ";" +
                            "NOT (someRole: $y, otherRole: $q) isa " + connection + ";" +
                            "(someRole: $y, otherRole: $z) isa binary;" +
                            "get;"
            ));


            List<ConceptMap> fullAnswers = tx.execute(Graql.<GetQuery>parse(
                    "match " +
                            "$q isa " + unwantedLabel + ";" +
                            "(someRole: $x, otherRole: $y) isa binary;" +
                            "(someRole: $y, otherRole: $z) isa binary;" +
                            "get;"
            ));

            Set<ConceptMap> expectedAnswers = fullAnswers.stream()
                    .filter(ans -> !thingsRelated(
                            ImmutableMap.of(
                                    ans.get("y").asThing(), tx.getRole("someRole"),
                                    ans.get("q").asThing(), tx.getRole("otherRole"))
                            ,
                            Label.of(connection),
                            tx)
                    ).collect(toSet());

            assertCollectionsEqual(
                    expectedAnswers,
                    answersWithoutSpecificConnection
            );

        }
    }

    @Test
    public void entitiesWithoutSpecificAttributeValue(){
        try(Transaction tx = negationSession.transaction(Transaction.Type.WRITE)) {
            String specificStringValue = "value";
            List<ConceptMap> answersWithoutSpecificStringValue = tx.execute(Graql.<GetQuery>parse(
                    "match " +
                            "$x isa entity;" +
                            "NOT $x has attribute '" + specificStringValue + "';" +
                            "get;"
            ));

            List<ConceptMap> fullAnswers = tx.execute(Graql.<GetQuery>parse("match $x isa entity;get;"));
            Set<ConceptMap> expectedAnswers = fullAnswers.stream().filter(ans -> ans.get("x").asThing().attributes().noneMatch(a -> a.value().equals(specificStringValue))).collect(toSet());
            assertCollectionsEqual(
                    expectedAnswers,
                    answersWithoutSpecificStringValue
            );
        }
    }

    @Test
    public void entitiesWithAttributeNotEqualToSpecificValue(){
        try(Transaction tx = negationSession.transaction(Transaction.Type.WRITE)) {
            String specificStringValue = "unattached";

            List<ConceptMap> answersWithoutSpecificStringValue = tx.execute(Graql.<GetQuery>parse(
                    "match " +
                            "$x has attribute $r;" +
                            "NOT $r == '" + specificStringValue + "';" +
                            "get;"
            ));

            List<ConceptMap> fullAnswers = tx.execute(Graql.<GetQuery>parse("match $x has attribute $r;get;"));

            assertCollectionsEqual(
                    fullAnswers.stream().filter(ans -> !ans.get("r").asAttribute().value().equals(specificStringValue)).collect(toSet()),
                    answersWithoutSpecificStringValue
            );
        }
    }

    //TODO update expected answers
    @Ignore
    @Test
    public void negateResource_UserDefinedResourceVariable(){
        try(Transaction tx = negationSession.transaction(Transaction.Type.WRITE)) {
            String specificStringValue = "unattached";

            Statement hasPattern = Pattern.var("x").has("attribute", Pattern.var("r").val(specificStringValue));
            Pattern pattern =
            Pattern.and(
                    Pattern.var("x").isa("entity"),
                    Pattern.not(hasPattern)
            );
            List<ConceptMap> answersWithoutSpecificStringValue = tx.execute(Graql.match(pattern).get());
            List<ConceptMap> fullAnswers = tx.execute(Graql.<GetQuery>parse("match $x has attribute $r;get;"));

            //TODO
            assertCollectionsEqual(
                    fullAnswers.stream().filter(ans -> !ans.get("r").asAttribute().value().equals(specificStringValue)).collect(toSet()),
                    answersWithoutSpecificStringValue
            );
        }
    }

    @Test
    public void entitiesHavingAttributesThatAreNotOfSpecificType(){
        try(Transaction tx = negationSession.transaction(Transaction.Type.WRITE)) {
            String specificTypeLabel = "anotherType";
            EntityType specificType = tx.getEntityType(specificTypeLabel);

            List<ConceptMap> answersWithoutSpecificType = tx.execute(Graql.<GetQuery>parse(
                    "match " +
                            "$x has attribute $r;" +
                            "NOT $x isa " + specificTypeLabel +
                            ";get;"
            ));

            List<ConceptMap> fullAnswers = tx.execute(Graql.<GetQuery>parse("match $x has attribute $r;get;"));

            assertCollectionsEqual(
                    fullAnswers.stream()
                            .filter(ans -> !ans.get("x").asThing().type().equals(specificType)).collect(toSet()),
                    answersWithoutSpecificType
            );
        }
    }

    @Test
    public void entitiesNotHavingRolePlayersInRelations(){
        try(Transaction tx = negationSession.transaction(Transaction.Type.WRITE)) {
            String connection = "relationship";
            List<ConceptMap> fullAnswers = tx.execute(Graql.<GetQuery>parse("match $x has attribute $r;get;"));
            List<ConceptMap> answersNotPlayingInRelation = tx.execute(Graql.<GetQuery>parse("match " +
                    "$x has attribute $r;"+
                    "NOT ($x) isa relationship;" +
                    "get;"
            ));

            Set<ConceptMap> expectedAnswers = fullAnswers.stream()
                    .filter(ans -> !thingsRelated(
                            ImmutableMap.of(ans.get("x").asThing(), tx.getMetaRole()),
                            Label.of(connection),
                            tx))
                    .collect(toSet());
            assertCollectionsEqual(
                    expectedAnswers,
                    answersNotPlayingInRelation
            );
        }
    }

    @Test
    public void negateMultiplePropertyStatement(){
        try(Transaction tx = negationSession.transaction(Transaction.Type.WRITE)) {
            String specificValue = "value";
            String specificTypeLabel = "type";
            String anotherSpecificValue = "attached";

            List<ConceptMap> answersWithoutSpecifcTypeAndValue = tx.execute(Graql.<GetQuery>parse(
                    "match " +
                            "$x has attribute $r;" +
                            "NOT $x isa " + specificTypeLabel +
                            " has resource-string " + "'" + specificValue + "'" +
                            " has derived-resource-string " + "'" + anotherSpecificValue + "';" +
                            "get;"
            ));

            List<ConceptMap> equivalentAnswers = tx.execute(Graql.<GetQuery>parse(
                    "match " +
                            "$x has attribute $r;" +
                            "NOT {" +
                                "{" +
                                    "$x isa " + specificTypeLabel + ";" +
                                    "$x has resource-string " + "'" + specificValue + "'" + ";" +
                                    "$x has derived-resource-string " + "'" + anotherSpecificValue + "';" +
                                "}" +
                            "};" +
                            "get;"
            ));

            assertCollectionsEqual(equivalentAnswers, answersWithoutSpecifcTypeAndValue);
        }
    }

    @Test
    public void negateMultipleStatements(){
        try(Transaction tx = negationSession.transaction(Transaction.Type.WRITE)) {
            String anotherSpecificValue = "value";
            String specificTypeLabel = "anotherType";
            EntityType specificType = tx.getEntityType(specificTypeLabel);

            List<ConceptMap> fullAnswers = tx.execute(Graql.<GetQuery>parse("match $x has attribute $r;get;"));

            List<ConceptMap> answersWithoutSpecifcTypeAndValue = tx.execute(Graql.<GetQuery>parse(
                    "match " +
                            "$x has attribute $r;" +
                            "NOT $x isa " + specificTypeLabel + ";" +
                            "NOT $r == '" + anotherSpecificValue + "';" +
                            "get;"
            ));

            assertCollectionsEqual(
                    fullAnswers.stream()
                            .filter(ans -> !ans.get("r").asAttribute().value().equals(anotherSpecificValue))
                            .filter(ans -> !ans.get("x").asThing().type().equals(specificType))
                            .collect(toSet()),
                    answersWithoutSpecifcTypeAndValue
            );

        }
    }

    @Test
    public void whenNegatingGroundTransitiveRelation_queryTerminates(){
        try(Transaction tx = negationSession.transaction(Transaction.Type.WRITE)) {
            Concept start = tx.execute(Graql.<GetQuery>parse("match $x isa type has resource-string 'value';get;")).iterator().next().get("x");
            Concept end = tx.execute(Graql.<GetQuery>parse("match $x isa type has resource-string 'someString';get;")).iterator().next().get("x");

            List<ConceptMap> answers = tx.execute(Graql.<GetQuery>parse(
                    "match " +
                            "NOT ($x, $y) isa derived-binary;" +
                            "$x id '" + start.id().getValue() + "';" +
                            "$y id '" + end.id().getValue() + "';" +
                            "get;"
            ));
            assertTrue(answers.isEmpty());
        }
    }

    private boolean thingsRelated(Map<Thing, Role> thingMap, Label relation, Transaction tx){
        RelationType relationshipType = tx.getRelationshipType(relation.getValue());
        boolean inferrable = relationshipType.subs().flatMap(SchemaConcept::thenRules).findFirst().isPresent();

        if (!inferrable){
            return relationshipType
                    .instances()
                    .anyMatch(r -> thingMap.entrySet().stream().allMatch(e -> r.rolePlayers(e.getValue()).anyMatch(rp -> rp.equals(e.getKey()))));
        }

        Statement pattern = Pattern.var();
        Set<Statement> patterns = new HashSet<>();
        for(Map.Entry<Thing, Role> entry : thingMap.entrySet()){
            Role role = entry.getValue();
            Thing thing = entry.getKey();
            Statement rpVar = Pattern.var();
            patterns.add(rpVar.id(thing.id().getValue()));
            pattern = pattern.rel(role.label().getValue(), rpVar);
        }
        patterns.add(pattern.isa(relation.getValue()));
        return tx.stream(Graql.match(Pattern.and(patterns)).get()).findFirst().isPresent();
    }
}
