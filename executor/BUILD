# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.

load("@typedb_dependencies//tool/checkstyle:rules.bzl", "checkstyle_test")
load("@rules_rust//rust:defs.bzl", "rust_library", "rust_test")
package(default_visibility = ["//visibility:public"])

rust_library(
    name = "executor",
    crate_root = "lib.rs",
    srcs = glob([
        "*.rs",
        "*/*.rs",
        "*/*/*.rs",
        "*/*/*/*.rs",
    ], exclude=["tests/**"]),
    deps = [
        "//answer",
        "//common/bytes",
        "//common/error",
        "//common/iterator",
        "//common/lending_iterator",
        "//common/logger",
        "//common/primitive",
        "//compiler",
        "//concept",
        "//encoding",
        "//ir",
        "//resource",
        "//storage",

        "@typeql//rust:typeql",

        "@crates//:chrono",
        "@crates//:itertools",
        "@crates//:regex",
        "@crates//:tracing",
        "@crates//:tokio",
        "@crates//:unicase",
    ],
    proc_macro_deps = [
        "@crates//:paste",
    ],
)

rust_test(
    name = "test_crate_executor",
    crate = ":executor",
    deps = [],
)

checkstyle_test(
    name = "checkstyle",
    include = glob(["*", "**/*.rs"]),
    exclude = glob([
        "Cargo.*",
    ]),
    license_type = "mpl-header",
)
