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

package grakn.core.graql.internal.executor.property;

import com.google.common.collect.ImmutableSet;
import grakn.core.graql.admin.Atomic;
import grakn.core.graql.admin.ReasonerQuery;
import grakn.core.graql.concept.ConceptId;
import grakn.core.graql.concept.Type;
import grakn.core.graql.internal.executor.WriteExecutor;
import grakn.core.graql.internal.gremlin.EquivalentFragmentSet;
import grakn.core.graql.internal.gremlin.sets.EquivalentFragmentSets;
import grakn.core.graql.internal.reasoner.atom.binary.IsaAtom;
import grakn.core.graql.internal.reasoner.atom.predicate.IdPredicate;
import grakn.core.graql.query.pattern.PositiveStatement;
import grakn.core.graql.query.pattern.Statement;
import grakn.core.graql.query.pattern.Variable;
import grakn.core.graql.query.pattern.property.IsaExplicitProperty;
import grakn.core.graql.query.pattern.property.IsaProperty;
import grakn.core.graql.query.pattern.property.RelationProperty;
import grakn.core.graql.query.pattern.property.VarProperty;

import java.util.Set;

import static grakn.core.graql.internal.reasoner.utils.ReasonerUtils.getIdPredicate;

public class IsaExecutor implements PropertyExecutor.Insertable,
                                    PropertyExecutor.Matchable,
                                    PropertyExecutor.Atomable {

    private final Variable var;
    private final IsaProperty property;

    IsaExecutor(Variable var, IsaProperty property) {
        this.var = var;
        this.property = property;
    }

    @Override
    public Set<PropertyExecutor.Writer> insertExecutors() {
        return ImmutableSet.of(new InsertIsa());
    }

    @Override
    public Set<EquivalentFragmentSet> matchFragments() {
        Variable directTypeVar = new Variable();
        return ImmutableSet.of(
                EquivalentFragmentSets.isa(property, var, directTypeVar, true),
                EquivalentFragmentSets.sub(property, directTypeVar, property.type().var())
        );
    }

    @Override
    public boolean mappable(Statement statement) {
        //IsaProperty is unique within a var, so skip if this is a relation
        return !statement.hasProperty(RelationProperty.class);
    }

    @Override
    public Atomic atomic(ReasonerQuery parent, Statement statement, Set<Statement> otherStatements) {
        //IsaProperty is unique within a var, so skip if this is a relation
        if (!mappable(statement)) return null;

        Variable varName = var.asUserDefined();
        Variable typeVar = property.type().var();

        IdPredicate predicate = getIdPredicate(typeVar, property.type(), otherStatements, parent);
        ConceptId predicateId = predicate != null ? predicate.getPredicate() : null;

        //isa part
        Statement isaVar;

        if (property instanceof IsaExplicitProperty) {
            isaVar = new PositiveStatement(varName).isaExplicit(new PositiveStatement(typeVar));
        } else {
            isaVar = new PositiveStatement(varName).isa(new PositiveStatement(typeVar));
        }

        return IsaAtom.create(varName, typeVar, isaVar, predicateId, parent);
    }

    public static class IsaExplicitExecutor extends IsaExecutor {

        public IsaExplicitExecutor(Variable var, IsaProperty property) {
            super(var, property);
        }

        @Override
        public Set<EquivalentFragmentSet> matchFragments() {
            return ImmutableSet.of(EquivalentFragmentSets.isa(super.property,
                                                              super.var,
                                                              super.property.type().var(),
                                                              true)
            );
        }
    }

    private class InsertIsa implements PropertyExecutor.Writer {

        @Override
        public Variable var() {
            return var;
        }

        @Override
        public VarProperty property() {
            return property;
        }

        @Override
        public Set<Variable> requiredVars() {
            return ImmutableSet.of(property.type().var());
        }

        @Override
        public Set<Variable> producedVars() {
            return ImmutableSet.of(var);
        }

        @Override
        public void execute(WriteExecutor executor) {
            Type type = executor.getConcept(property.type().var()).asType();
            executor.getBuilder(var).isa(type);
        }
    }
}
