/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

use std::fmt;

use super::{ThingPosition, TypeSource, ValueSource};

#[derive(Debug)]
pub enum ConceptInstruction {
    PutObject(PutObject),
    PutAttribute(PutAttribute),
}
impl ConceptInstruction {
    pub(crate) fn inserted_type(&self) -> &TypeSource {
        match self {
            ConceptInstruction::PutObject(inner) => &inner.type_,
            ConceptInstruction::PutAttribute(inner) => &inner.type_,
        }
    }
    pub(crate) fn inserted_position(&self) -> &ThingPosition {
        match self {
            ConceptInstruction::PutObject(inner) => &inner.write_to,
            ConceptInstruction::PutAttribute(inner) => &inner.write_to,
        }
    }
}
impl fmt::Display for ConceptInstruction {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            ConceptInstruction::PutObject(_) => write!(f, "Put object"),
            ConceptInstruction::PutAttribute(_) => write!(f, "Put attribute"),
        }
    }
}

#[derive(Debug)]
pub enum ConnectionInstruction {
    Has(Has),     // TODO: Ordering
    Links(Links), // TODO: Ordering
}

impl fmt::Display for ConnectionInstruction {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::Has(_) => write!(f, "Put has"),
            Self::Links(_) => write!(f, "Put links"),
        }
    }
}

// TODO: Move to storing the inserted thing directly into the output row
#[derive(Debug)]
pub struct PutObject {
    pub type_: TypeSource,
    pub write_to: ThingPosition,
}

#[derive(Debug)]
pub struct PutAttribute {
    pub type_: TypeSource,
    pub value: ValueSource,
    pub write_to: ThingPosition,
}

#[derive(Debug)]
pub struct Has {
    pub owner: ThingPosition,
    pub attribute: ThingPosition,
}

#[derive(Debug)]
pub struct Links {
    pub relation: ThingPosition,
    pub player: ThingPosition,
    pub role: TypeSource,
}
