/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

use std::{
    collections::HashMap,
    fmt,
    hash::{Hash, Hasher},
    ops::Range,
    sync::Arc,
};

use bytes::{byte_array::ByteArray, Bytes};
use itertools::Itertools;
use primitive::either::Either;
use resource::constants::{
    encoding::StructFieldIDUInt,
    snapshot::{BUFFER_KEY_INLINE, BUFFER_VALUE_INLINE},
};
use storage::{
    key_value::StorageKey,
    snapshot::{iterator::SnapshotIteratorError, ReadableSnapshot, WritableSnapshot},
};

use crate::{
    error::EncodingError,
    graph::{
        common::value_hasher::HashedID,
        definition::{
            definition_key::DefinitionKey,
            r#struct::{StructDefinition, StructDefinitionField},
        },
        thing::{
            vertex_attribute::{AttributeID, AttributeVertex, StructAttributeID, ValueEncodingLength},
            vertex_generator::ThingVertexGenerator,
        },
        type_::vertex::{TypeID, TypeVertex},
        Typed,
    },
    layout::prefix::{Prefix, PrefixID},
    value::{
        boolean_bytes::BooleanBytes, date_bytes::DateBytes, date_time_bytes::DateTimeBytes,
        date_time_tz_bytes::DateTimeTZBytes, decimal_bytes::DecimalBytes, double_bytes::DoubleBytes,
        duration_bytes::DurationBytes, integer_bytes::IntegerBytes, string_bytes::StringBytes, value::Value,
        ValueEncodable,
    },
    AsBytes, EncodingKeyspace, Keyable, Prefixed,
};

// TODO: Do we want to encode / decode the full struct all the time? or just wrap bytes and
// write some accessor logic so we efficiently deserialize only the fields we need.
#[derive(Debug, Clone, Eq, PartialEq)]
pub struct StructValue<'a> {
    definition_key: DefinitionKey,
    // a map allows empty fields to not be recorded at all
    fields: HashMap<StructFieldIDUInt, Value<'a>>,
}

impl<'a> StructValue<'a> {
    pub fn new(definition_key: DefinitionKey, fields: HashMap<StructFieldIDUInt, Value<'a>>) -> StructValue<'a> {
        StructValue { definition_key, fields }
    }

    pub fn build(
        definition_key: DefinitionKey,
        struct_definition: StructDefinition,
        value: HashMap<String, Value<'a>>,
    ) -> Result<StructValue<'a>, Vec<EncodingError>> {
        let mut fields: HashMap<StructFieldIDUInt, Value<'a>> = HashMap::new();
        let mut errors: Vec<EncodingError> = Vec::new();
        for (field_name, field_id) in struct_definition.field_names {
            let field_definition: &StructDefinitionField = struct_definition.fields.get(&field_id).unwrap();
            if let Some(value) = value.get(&field_name) {
                if field_definition.value_type == value.value_type() {
                    fields.entry(field_id).or_insert_with(|| value.clone());
                } else {
                    errors.push(EncodingError::StructFieldValueTypeMismatch {
                        struct_name: struct_definition.name.clone(),
                        field_name,
                        expected: field_definition.value_type.clone(),
                    })
                }
            } else if !field_definition.optional {
                errors.push(EncodingError::StructMissingRequiredField {
                    struct_name: struct_definition.name.clone(),
                    field_name,
                })
            }
        }

        if errors.is_empty() {
            Ok(StructValue { definition_key, fields })
        } else {
            Err(errors)
        }
    }

    pub fn definition_key(&self) -> &DefinitionKey {
        &self.definition_key
    }

    pub fn fields(&self) -> &HashMap<StructFieldIDUInt, Value<'a>> {
        &self.fields
    }

    // Deeply nested structs may take up a lot of space with the u16 path.
    pub fn create_index_entries(
        &self,
        snapshot: &impl WritableSnapshot,
        thing_vertex_generator: &ThingVertexGenerator,
        attribute: &AttributeVertex,
    ) -> Result<Vec<StructIndexEntry<'_>>, Arc<SnapshotIteratorError>> {
        let mut acc: Vec<StructIndexEntry<'_>> = Vec::new();
        let mut path: Vec<StructFieldIDUInt> = Vec::new();
        Self::create_index_entries_recursively(
            snapshot,
            thing_vertex_generator.hasher(),
            attribute,
            &self.fields,
            &mut path,
            &mut acc,
        )?;
        Ok(acc)
    }

    fn create_index_entries_recursively(
        snapshot: &impl WritableSnapshot,
        hasher: &impl Fn(&[u8]) -> u64,
        attribute: &AttributeVertex,
        fields: &HashMap<StructFieldIDUInt, Value<'a>>,
        path: &mut Vec<StructFieldIDUInt>,
        acc: &mut Vec<StructIndexEntry<'static>>,
    ) -> Result<(), Arc<SnapshotIteratorError>> {
        for (idx, value) in fields.iter() {
            path.push(*idx);
            if let Value::Struct(struct_val) = value {
                Self::create_index_entries_recursively(snapshot, hasher, attribute, struct_val.fields(), path, acc)?;
            } else {
                acc.push(StructIndexEntry::build(snapshot, hasher, path, value, attribute)?);
            }
            let popped = path.pop().unwrap();
            debug_assert_eq!(*idx, popped);
        }
        Ok(())
    }
}

impl Hash for StructValue<'_> {
    fn hash<H: Hasher>(&self, state: &mut H) {
        Hash::hash(&self.definition_key, state);
        for (id, value) in self.fields.iter() {
            Hash::hash(id, state);
            Hash::hash(value, state);
        }
    }
}

// Prints struct with inner values, but without the right labels, since these are not available inline
impl fmt::Display for StructValue<'_> {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{{")?;
        for (field_id, value) in self.fields.iter().sorted_by_key(|(id, _)| **id) {
            write!(f, "_{field_id}: {value}")?;
        }
        write!(f, "}}")
    }
}

pub struct StructIndexEntryKey {
    key_bytes: Bytes<'static, BUFFER_KEY_INLINE>,
}

pub struct StructIndexEntry<'a> {
    pub key: StructIndexEntryKey,
    pub value: Option<Bytes<'a, BUFFER_VALUE_INLINE>>,
}

impl StructIndexEntryKey {
    pub fn new(key_bytes: Bytes<'_, BUFFER_KEY_INLINE>) -> Self {
        Self { key_bytes: Bytes::copy(&key_bytes) }
    }
}

impl<'a> StructIndexEntry<'a> {
    pub fn new(key: StructIndexEntryKey, value: Option<Bytes<'a, BUFFER_VALUE_INLINE>>) -> Self {
        Self { key, value }
    }

    pub fn attribute_vertex(&self) -> AttributeVertex {
        let bytes = &self.key.key_bytes;
        let attribute_type_id = TypeID::decode(bytes[StructIndexEntry::ENCODING_TYPEID_RANGE].try_into().unwrap());
        let attribute_id = AttributeID::new(&bytes[(bytes.len() - StructAttributeID::LENGTH)..bytes.len()]);
        AttributeVertex::new(attribute_type_id, attribute_id)
    }
}

impl StructIndexEntry<'static> {
    const PREFIX: Prefix = Prefix::IndexValueToStruct;

    const ENCODING_PREFIX_RANGE: Range<usize> = 0..PrefixID::LENGTH;
    const ENCODING_VALUE_TYPE_RANGE: Range<usize> =
        Self::ENCODING_PREFIX_RANGE.end..{ Self::ENCODING_PREFIX_RANGE.end + 1 };
    const ENCODING_TYPEID_RANGE: Range<usize> =
        Self::ENCODING_VALUE_TYPE_RANGE.end..{ Self::ENCODING_VALUE_TYPE_RANGE.end + TypeID::LENGTH };

    pub fn build(
        snapshot: &impl ReadableSnapshot,
        hasher: &impl Fn(&[u8]) -> u64,
        path_to_field: &[StructFieldIDUInt],
        value: &Value<'_>,
        attribute: &AttributeVertex,
    ) -> Result<StructIndexEntry<'static>, Arc<SnapshotIteratorError>> {
        debug_assert_eq!(Prefix::VertexAttribute, attribute.prefix());
        let mut buf = Self::build_prefix_typeid_path_value_into_buf(
            snapshot,
            hasher,
            path_to_field,
            value,
            attribute.type_id_(),
        )?;
        buf.extend_from_slice(attribute.attribute_id().bytes());

        let value = match &value {
            Value::Boolean(_)
            | Value::Integer(_)
            | Value::Double(_)
            | Value::Decimal(_)
            | Value::Date(_)
            | Value::Datetime(_)
            | Value::DatetimeTz(_)
            | Value::Duration(_) => None,
            Value::String(value) => Some(StringBytes::<BUFFER_VALUE_INLINE>::build_owned(value).to_bytes()),
            Value::Struct(_) => unreachable!(),
        };
        Ok(Self { key: StructIndexEntryKey::new(Bytes::copy(buf.as_slice())), value })
    }

    pub fn build_prefix_typeid_path_value(
        snapshot: &impl ReadableSnapshot,
        thing_vertex_generator: &ThingVertexGenerator,
        path_to_field: &[StructFieldIDUInt],
        value: &Value<'_>,
        attribute_type: &TypeVertex,
    ) -> Result<StorageKey<'static, BUFFER_KEY_INLINE>, Arc<SnapshotIteratorError>> {
        let buf = Self::build_prefix_typeid_path_value_into_buf(
            snapshot,
            thing_vertex_generator.hasher(),
            path_to_field,
            value,
            attribute_type.type_id_(),
        )?;
        Ok(StorageKey::new_owned(Self::KEYSPACE, ByteArray::copy(buf.as_slice())))
    }

    fn build_prefix_typeid_path_value_into_buf(
        snapshot: &impl ReadableSnapshot,
        hasher: &impl Fn(&[u8]) -> u64,
        path_to_field: &[StructFieldIDUInt],
        value: &Value<'_>,
        attribute_type_id: TypeID,
    ) -> Result<Vec<u8>, Arc<SnapshotIteratorError>> {
        let mut buf: Vec<u8> = Vec::with_capacity(
            PrefixID::LENGTH +  // Prefix::IndexValueToStruct
                PrefixID::LENGTH +      // ValueTypeCategory of indexed value
                TypeID::LENGTH +        // TypeID of Attribute being indexed
                2*path_to_field.len() + // Path to the field
                ValueEncodingLength::Long.length() + // Value for the field.
                StructAttributeID::LENGTH, // ID of the attribute being indexed
        );

        buf.push(Self::PREFIX.prefix_id().byte);
        buf.extend_from_slice(&value.value_type().category().to_bytes());
        buf.extend_from_slice(&attribute_type_id.to_bytes());
        for p in path_to_field {
            buf.extend_from_slice(&p.to_be_bytes())
        }
        match &value {
            Value::Boolean(value) => buf.extend_from_slice(&BooleanBytes::build(*value).bytes()),
            Value::Integer(value) => buf.extend_from_slice(&IntegerBytes::build(*value).bytes()),
            Value::Double(value) => buf.extend_from_slice(&DoubleBytes::build(*value).bytes()),
            Value::Decimal(value) => buf.extend_from_slice(&DecimalBytes::build(*value).bytes()),
            Value::Date(value) => buf.extend_from_slice(&DateBytes::build(*value).bytes()),
            Value::Datetime(value) => buf.extend_from_slice(&DateTimeBytes::build(*value).bytes()),
            Value::DatetimeTz(value) => buf.extend_from_slice(&DateTimeTZBytes::build(*value).bytes()),
            Value::Duration(value) => buf.extend_from_slice(&DurationBytes::build(*value).bytes()),
            Value::String(value) => {
                let string_bytes = StringBytes::<0>::build_ref(value);
                Self::encode_string_into(snapshot, hasher, string_bytes.as_reference(), &mut buf)?;
            }
            Value::Struct(_) => unreachable!(),
        };
        Ok(buf)
    }
}

impl StructIndexEntry<'_> {
    const KEYSPACE: EncodingKeyspace = EncodingKeyspace::DefaultOptimisedPrefix11;
    const STRING_FIELD_LENGTH: usize = 17;
    const STRING_FIELD_HASHID_LENGTH: usize = 9;
    const STRING_FIELD_HASHED_PREFIX_LENGTH: usize = Self::STRING_FIELD_LENGTH - Self::STRING_FIELD_HASHID_LENGTH;
    const STRING_FIELD_INLINE_LENGTH: usize = Self::STRING_FIELD_LENGTH - 1;

    fn encode_string_into<const INLINE_SIZE: usize>(
        snapshot: &impl ReadableSnapshot,
        hasher: &impl Fn(&[u8]) -> u64,
        string_bytes: StringBytes<INLINE_SIZE>,
        buf: &mut Vec<u8>,
    ) -> Result<(), Arc<SnapshotIteratorError>> {
        if Self::is_string_inlineable(string_bytes.as_reference()) {
            let mut inline_bytes: [u8; StructIndexEntry::STRING_FIELD_INLINE_LENGTH] =
                [0; { StructIndexEntry::STRING_FIELD_INLINE_LENGTH }];
            inline_bytes[0..string_bytes.bytes().len()].copy_from_slice(string_bytes.bytes());
            buf.extend_from_slice(&inline_bytes);
            buf.push(string_bytes.len() as u8);
        } else {
            buf.extend_from_slice(&string_bytes.bytes()[0..Self::STRING_FIELD_HASHED_PREFIX_LENGTH]);
            let prefix_key: Bytes<'_, BUFFER_KEY_INLINE> = Bytes::reference(buf.as_slice());
            let disambiguated_hash_bytes: [u8; StructIndexEntry::STRING_FIELD_HASHID_LENGTH] =
                match Self::find_existing_or_next_disambiguated_hash(
                    snapshot,
                    hasher,
                    Self::KEYSPACE,
                    &prefix_key,
                    string_bytes.bytes(),
                )? {
                    Either::First(hash) => hash,
                    Either::Second(hash) => hash,
                };
            buf.extend_from_slice(&disambiguated_hash_bytes);
        }
        Ok(())
    }

    fn is_string_inlineable<const INLINE_SIZE: usize>(string_bytes: StringBytes<INLINE_SIZE>) -> bool {
        string_bytes.len() < Self::STRING_FIELD_HASHED_PREFIX_LENGTH + Self::STRING_FIELD_HASHID_LENGTH
    }
}

impl HashedID<{ StructIndexEntry::STRING_FIELD_HASHID_LENGTH }> for StructIndexEntry<'_> {
    const FIXED_WIDTH_KEYS: bool = { Prefix::IndexValueToStruct.fixed_width_keys() };
}

impl AsBytes<BUFFER_KEY_INLINE> for StructIndexEntryKey {
    fn to_bytes(self) -> Bytes<'static, BUFFER_KEY_INLINE> {
        self.key_bytes
    }
}

impl Keyable<BUFFER_KEY_INLINE> for StructIndexEntryKey {
    fn keyspace(&self) -> EncodingKeyspace {
        StructIndexEntry::KEYSPACE
    }
}
