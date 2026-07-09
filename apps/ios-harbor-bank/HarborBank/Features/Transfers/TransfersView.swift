import SwiftUI

struct TransfersView: View {
    @EnvironmentObject private var session: SessionStore
    @State private var accounts: [Account] = []
    @State private var toAccountId = ""
    @State private var amount = "25.00"
    @State private var message: String?
    @State private var busy = false

    var body: some View {
        NavigationStack {
            Form {
                Section("From") {
                    if let from = accounts.first {
                        Text("\(from.accountNumber) · \(MoneyFormat.string(from.balance, currency: from.currency))")
                    } else {
                        Text("Open an account on Home first").foregroundStyle(.secondary)
                    }
                }
                Section("To account ID") {
                    TextField("Destination account UUID", text: $toAccountId)
                        .textInputAutocapitalization(.never)
                }
                Section("Amount") {
                    TextField("Amount", text: $amount).keyboardType(.decimalPad)
                }
                if let message {
                    Section { Text(message).foregroundStyle(message.contains("failed") ? .red : HarborTheme.sea) }
                }
                Section {
                    Button("Send transfer") { Task { await send() } }
                        .disabled(busy || accounts.isEmpty || toAccountId.isEmpty)
                } footer: {
                    Text("Successful and failed transfers automatically create Kafka-driven alerts on the Alerts tab.")
                }
            }
            .navigationTitle("Move money")
            .task { await load() }
        }
    }

    private func load() async {
        guard let token = session.accessToken, let customerId = session.customerId else { return }
        accounts = (try? await HarborAPI.accounts(token: token, customerId: customerId)) ?? []
        if accounts.count > 1, toAccountId.isEmpty {
            toAccountId = accounts[1].id
        }
    }

    private func send() async {
        guard let token = session.accessToken, let from = accounts.first,
              let value = Double(amount) else { return }
        busy = true
        defer { busy = false }
        do {
            let txn = try await HarborAPI.transfer(token: token, from: from.id, to: toAccountId, amount: value, currency: "USD")
            message = "Transfer \(txn.status). Check Alerts for the push/in-app notification."
            await load()
        } catch {
            message = "Transfer failed: \(error.localizedDescription)"
        }
    }
}
