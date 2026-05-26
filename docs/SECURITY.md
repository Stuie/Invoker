# Security policy

## Reporting a vulnerability

Open an issue at [github.com/essteeyou/invoker/issues](https://github.com/essteeyou/invoker/issues) and tag it with the `security` label. Include a clear reproducer (steps, sample input, expected vs actual behaviour).

There is **no private disclosure channel** for Invoker — reports are made in the open. The trade-off is appropriate for what this software does: Invoker is a desktop launcher with no server component, no credentials handling, no persistent network listening, and no telemetry. A hypothetical vulnerability has a small blast radius.

Pull requests fixing reported issues are welcome.

## Scope

This policy covers code in this repository (the Invoker launcher). It does **not** cover:

- The XMage client/server itself ([magefree/mage](https://github.com/magefree/mage)).
- The xmage.today distribution server.
- Vulnerabilities in third-party dependencies (please report those upstream).
