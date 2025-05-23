/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

use std::{
    collections::HashMap,
    fmt,
    hash::{DefaultHasher, Hash, Hasher},
};

use itertools::Itertools;
use structural_equality::StructuralEquality;

use crate::{pattern::IrID, pipeline::function_signature::FunctionID};

#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub struct FunctionCall<ID> {
    function_id: FunctionID,
    arguments: Vec<ID>,
}

impl<ID> FunctionCall<ID> {
    pub fn new(function_id: FunctionID, arguments: Vec<ID>) -> Self {
        Self { function_id, arguments }
    }
}

impl<ID: IrID> FunctionCall<ID> {
    pub fn function_id(&self) -> FunctionID {
        self.function_id.clone()
    }

    pub fn argument_ids(&self) -> impl Iterator<Item = ID> + '_ {
        self.arguments.iter().cloned()
    }

    pub fn map<T: Clone + Ord>(self, mapping: &HashMap<ID, T>) -> FunctionCall<T> {
        FunctionCall::new(self.function_id.clone(), self.arguments.iter().map(|var| var.map(mapping)).collect())
    }
}

impl<ID: StructuralEquality + Ord> StructuralEquality for FunctionCall<ID> {
    fn hash(&self) -> u64 {
        let mut hasher = DefaultHasher::new();
        self.function_id.hash_into(&mut hasher);
        self.arguments.hash_into(&mut hasher);
        hasher.finish()
    }

    fn equals(&self, other: &Self) -> bool {
        self.function_id.equals(&other.function_id) && self.arguments.equals(&other.arguments)
    }
}

impl<ID: IrID> fmt::Display for FunctionCall<ID> {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        let formatted_args = self.arguments.iter().map(|call_var| format!("{call_var}")).join(", ");

        write!(f, "fn_{}({})", self.function_id, formatted_args)
    }
}
