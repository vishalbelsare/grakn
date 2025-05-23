/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

use compiler::annotation::FunctionAnnotationError;
use encoding::error::EncodingError;
use error::typedb_error;
use ir::pipeline::{FunctionReadError, FunctionRepresentationError};
use typeql::common::Span;

pub mod function;
pub mod function_cache;
pub mod function_manager;

typedb_error! {
    pub FunctionError(component = "Function", prefix = "FUN") {
        FunctionNotFound(1, "Function was not found"),
        AllFunctionsTypeCheckFailure(2, "Type checking all functions currently defined failed.", typedb_source: Box<FunctionAnnotationError>),
        CommittedFunctionsTypeCheck(3, "Type checking stored functions failed.", typedb_source: Box<FunctionAnnotationError>),
        FunctionTranslation(4, "Failed to translate TypeQL function into internal representation", typedb_source: FunctionRepresentationError),
        FunctionAlreadyExists(
            5,
            "A function with name '{name}' already exists",
            name: String,
            source_span: Option<Span>,
        ),
        CreateFunctionEncoding(6, "Encoding error while trying to create function.", source: EncodingError),
        FunctionRetrieval(7, "Error retrieving function.", typedb_source: FunctionReadError),
        CommittedFunctionParseError(8, "Error while parsing committed function.", typedb_source: typeql::Error),
        StratificationViolation(9, "Detected a recursive cycle through a negation or reduction: [{cycle_names}]", cycle_names: String),
    }
}
