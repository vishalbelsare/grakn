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

package grakn.core.graql.internal.reasoner.atom.property;

import com.google.auto.value.AutoValue;
import grakn.core.graql.admin.Atomic;
import grakn.core.graql.admin.ReasonerQuery;
import grakn.core.graql.concept.AttributeType;
import grakn.core.graql.internal.reasoner.atom.AtomicBase;
import grakn.core.graql.query.pattern.PositiveStatement;
import grakn.core.graql.query.pattern.Statement;
import grakn.core.graql.query.pattern.Variable;
import grakn.core.graql.query.pattern.property.DataTypeProperty;

/**
 * Atomic corresponding to DataTypeProperty.
 */
@AutoValue
public abstract class DataTypeAtom extends AtomicBase {

    @Override public abstract Statement getPattern();
    @Override public abstract ReasonerQuery getParentQuery();
    public abstract AttributeType.DataType<?> getDataType();

    public static DataTypeAtom create(Variable varName, DataTypeProperty prop, ReasonerQuery parent, AttributeType.DataType<?> dataType) {
        return new AutoValue_DataTypeAtom(varName, new PositiveStatement(varName).datatype(prop.dataType()), parent, dataType);
    }

    private static DataTypeAtom create(DataTypeAtom a, ReasonerQuery parent) {
        return new AutoValue_DataTypeAtom(a.getVarName(), a.getPattern(), parent, a.getDataType());
    }

    @Override
    public Atomic copy(ReasonerQuery parent) { return create(this, parent);}

    @Override
    public boolean isAlphaEquivalent(Object obj) {
        if (obj == null || this.getClass() != obj.getClass()) return false;
        if (obj == this) return true;
        DataTypeAtom a2 = (DataTypeAtom) obj;
        return this.getDataType().equals(a2.getDataType());
    }

    @Override
    public int alphaEquivalenceHashCode() {
        int hashCode = 1;
        hashCode = hashCode * 37 + this.getDataType().hashCode();
        return hashCode;
    }

    @Override
    public boolean isStructurallyEquivalent(Object obj) {
        return isAlphaEquivalent(obj);
    }

    @Override
    public int structuralEquivalenceHashCode() {
        return alphaEquivalenceHashCode();
    }

    @Override
    public boolean subsumes(Atomic atom) { return this.isAlphaEquivalent(atom); }
}
