/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

use ir::{
    pipeline::{function_signature::HashMapFunctionSignatureIndex, ParameterRegistry},
    translation::{match_::translate_match, PipelineTranslationContext},
    RepresentationError,
};
use typeql::query::stage::Stage;

#[test]
fn build_conjunction_constraints() {
    let empty_function_index = HashMapFunctionSignatureIndex::empty();

    let query = "match $person isa person, has name $name;";
    let parsed = typeql::parse_query(query).unwrap().into_structure();
    let typeql::query::QueryStructure::Pipeline(typeql::query::Pipeline { stages, .. }) = parsed else {
        unreachable!()
    };
    let Stage::Match(match_) = stages.first().unwrap() else { unreachable!() };
    eprintln!("{}\n", match_); // TODO
    eprintln!("{:#}\n", match_); // TODO
    eprintln!(
        "{}\n",
        translate_match(
            &mut PipelineTranslationContext::new(),
            &mut ParameterRegistry::new(),
            &empty_function_index,
            match_
        )
        .unwrap()
        .finish()
        .unwrap()
        .conjunction()
    );

    let query = "match
        $person isa $person-type, has $name-type $name;
        $person-type label person;
        $name-type label name;
    ";
    let parsed = typeql::parse_query(query).unwrap().into_structure();
    let typeql::query::QueryStructure::Pipeline(typeql::query::Pipeline { stages, .. }) = parsed else {
        unreachable!()
    };
    let Stage::Match(match_) = stages.first().unwrap() else { unreachable!() };
    eprintln!("{}\n", match_); // TODO
    eprintln!("{:#}\n", match_); // TODO
    eprintln!(
        "{}\n",
        translate_match(
            &mut PipelineTranslationContext::new(),
            &mut ParameterRegistry::new(),
            &empty_function_index,
            match_
        )
        .unwrap()
        .finish()
        .unwrap()
        .conjunction()
    );

    let query = "match
        $person isa $person-type;
        $person has $name;
        $name isa $name-type;
        $person-type label person;
        $name-type label name;
    ";
    let parsed = typeql::parse_query(query).unwrap().into_structure();
    let typeql::query::QueryStructure::Pipeline(typeql::query::Pipeline { stages, .. }) = parsed else {
        unreachable!()
    };
    let Stage::Match(match_) = stages.first().unwrap() else { unreachable!() };
    eprintln!("{}\n", match_); // TODO
    eprintln!("{:#}\n", match_); // TODO
    eprintln!(
        "{}\n",
        translate_match(
            &mut PipelineTranslationContext::new(),
            &mut ParameterRegistry::new(),
            &empty_function_index,
            match_
        )
        .unwrap()
        .finish()
        .unwrap()
        .conjunction()
    );

    // let mut block = FunctionalBlock::new();
    // let conjunction = block.conjunction_mut();

    // let var_person = conjunction.get_or_declare_variable("person").unwrap();
    // let var_name = conjunction.get_or_declare_variable("name").unwrap();
    // let var_person_type = conjunction.get_or_declare_variable("person_type").unwrap();
    // let var_name_type = conjunction.get_or_declare_variable("name_type").unwrap();

    // conjunction.constraints_mut().add_isa(IsaKind::Subtype, var_person, var_person_type).unwrap();
    // conjunction.constraints_mut().add_has(var_person, var_name).unwrap();
    // conjunction.constraints_mut().add_isa(IsaKind::Subtype, var_name, var_name_type).unwrap();
    // conjunction.constraints_mut().add_label(var_person_type, "person").unwrap();
    // conjunction.constraints_mut().add_label(var_name_type, "name").unwrap();
}

#[test]
fn variable_category_mismatch() {
    let empty_function_index = HashMapFunctionSignatureIndex::empty();

    let query = "match
        $person isa $person-type;
        $person-type isa $person;
    ";
    let parsed = typeql::parse_query(query).unwrap().into_structure();
    let typeql::query::QueryStructure::Pipeline(typeql::query::Pipeline { stages, .. }) = parsed else {
        unreachable!()
    };
    let Stage::Match(match_) = stages.first().unwrap() else { unreachable!() };
    let mut context = PipelineTranslationContext::new();
    let mut parameters = ParameterRegistry::new();
    let translated = translate_match(&mut context, &mut parameters, &empty_function_index, match_);
    assert!(matches!(translated.unwrap_err().as_ref(), &RepresentationError::VariableCategoryMismatch { .. }));

    // let mut block = FunctionalBlock::new();
    // let conjunction = block.conjunction_mut();

    // let var_person = conjunction.get_or_declare_variable("person").unwrap();
    // let var_person_type = conjunction.get_or_declare_variable("person_type").unwrap();

    // let result = conjunction.constraints_mut().add_isa(IsaKind::Subtype, var_person, var_person_type);
    // assert!(result.is_ok());
    // let result = conjunction.constraints_mut().add_isa(IsaKind::Subtype, var_person_type, var_person);
    // assert!(matches!(
    // result,
    // Err(PatternDefinitionError::VariableCategoryMismatch {
    // variable,
    // category_1: VariableCategory::Thing,
    // category_2: VariableCategory::Type,
    // ..
    // }) if variable == var_person_type
    // ));
}

#[test]
fn variable_category_narrowing() {
    let empty_function_index = HashMapFunctionSignatureIndex::empty();

    let query = "match $person isa $person-type, has $name-type $name;";
    let parsed = typeql::parse_query(query).unwrap().into_structure(); // TODO
    let typeql::query::QueryStructure::Pipeline(typeql::query::Pipeline { stages, .. }) = parsed else {
        unreachable!()
    };
    let Stage::Match(match_) = stages.first().unwrap() else { unreachable!() };
    eprintln!("{}\n", match_); // TODO
    eprintln!("{:#}\n", match_); // TODO
    let mut context = PipelineTranslationContext::new();
    let mut value_parameters = ParameterRegistry::new();
    eprintln!(
        "{}\n",
        translate_match(&mut context, &mut value_parameters, &empty_function_index, match_)
            .unwrap()
            .finish()
            .unwrap()
            .conjunction()
    );

    // let mut block = FunctionalBlock::new();
    // let conjunction = block.conjunction_mut();

    // let var_person = conjunction.get_or_declare_variable("person").unwrap();
    // let var_name = conjunction.get_or_declare_variable("name").unwrap();
    // let var_person_type = conjunction.get_or_declare_variable("person-type").unwrap();
    // let var_name_type = conjunction.get_or_declare_variable("name-type").unwrap();

    // conjunction.constraints_mut().add_isa(IsaKind::Subtype, var_person, var_person_type).unwrap();
    // conjunction.constraints_mut().add_isa(IsaKind::Subtype, var_name, var_name_type).unwrap();
    // // narrow name from Isa Thing to Attribute and person from Isa thing to Object owner
    // conjunction.constraints_mut().add_has(var_person, var_name).unwrap();

    // println!("{}", conjunction);
}
