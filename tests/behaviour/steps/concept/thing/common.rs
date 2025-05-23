/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

use encoding::graph::type_::Kind;
use macro_rules_attribute::apply;
use params;

use crate::{generic_step, Context};

#[apply(generic_step)]
#[step(expr = r"{kind} {var} {exists_or_doesnt}")]
async fn object_exists(
    context: &mut Context,
    kind: params::Kind,
    var: params::Var,
    exists_or_doesnt: params::ExistsOrDoesnt,
) {
    match kind.into_typedb() {
        Kind::Attribute => {
            let attribute = context.attributes.get(&var.name).expect("no variable {} in context.");
            exists_or_doesnt.check(attribute, &format!("variable {}", var.name));
        }
        Kind::Entity | Kind::Relation => {
            let object = context.objects.get(&var.name).expect("no variable {} in context.");
            exists_or_doesnt.check(object, &format!("variable {}", var.name));
        }
        Kind::Role => unreachable!("Encountered `role` as a kind"),
    }
}
