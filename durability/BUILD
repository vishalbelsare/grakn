# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.

load("@typedb_dependencies//tool/checkstyle:rules.bzl", "checkstyle_test")
load("@rules_rust//rust:defs.bzl", "rust_library", "rust_test")
package(default_visibility = [
    "//concept:__subpackages__",
    "//compiler:__subpackages__",
    "//database:__subpackages__",
    "//durability/tests:__subpackages__",
    "//durability/benches:__subpackages__",
    "//encoding:__subpackages__",
    "//executor:__subpackages__",
    "//function:__subpackages__",
    "//ir:__subpackages__",
    "//query:__subpackages__",
    "//storage:__subpackages__",
    "//tests:__subpackages__",
])

rust_library(
    name = "durability",
    crate_root = "durability.rs",
    srcs = glob([
        "*.rs",
    ]),
    deps = [
        "//common/error",
        "//common/logger",
        "//common/primitive",
        "//resource",

        "@crates//:itertools",
        "@crates//:lz4",
        "@crates//:serde",
        "@crates//:tracing",
    ]
)

rust_test(
    name = "test_crate_durability",
    crate = ":durability",
    deps = ["@crates//:tempdir"],
)

checkstyle_test(
    name = "checkstyle",
    include = glob(["*"]),
    exclude = glob([
        "Cargo.*",
    ]),
    license_type = "mpl-header",
)
