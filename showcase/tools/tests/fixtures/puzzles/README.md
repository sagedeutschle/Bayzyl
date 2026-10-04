# Native puzzle and Catan fixtures

Generated on 2026-10-04 from unmodified native Swift sources. The adjacent hashes identify the exact source inputs. No account data or user saves are involved.

From a checkout of the native app, compile `native-fixtures.swift` with the six sources listed in `native-source-hashes.json`, using `swiftc -parse-as-library`, then run the executable. The output contains `topology` and `vectors`; `native-parity.json` stores its `vectors`. The browser topology preserves the native vertex/edge ordering, with display coordinates rounded to 12 decimals. These fixtures prove seeded state parity; they do not establish native UI or bot-strength equivalence.

Run `node showcase/tools/tests/puzzle-games-check.mjs` and `node showcase/tools/tests/catan-check.mjs` from the website repository.
