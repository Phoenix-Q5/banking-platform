import SwiftUI

struct CardsView: View {
    @EnvironmentObject private var session: SessionStore
    @State private var accounts: [Account] = []
    @State private var cards: [Card] = []
    @State private var error: String?
    @State private var busy = false

    var body: some View {
        List {
            Section {
                Button("Issue debit card") { Task { await issue() } }.disabled(busy)
            }
            Section("Your cards") {
                ForEach(cards) { c in
                    HStack {
                        VStack(alignment: .leading) {
                            Text("•••• \(c.cardNumberLast4)").fontWeight(.semibold)
                            Text("\(c.cardNetwork) · \(c.cardType) · \(c.status)")
                                .font(.caption).foregroundStyle(.secondary)
                        }
                        Spacer()
                        Button(c.status == "FROZEN" ? "Unfreeze" : "Freeze") {
                            Task { await toggle(c) }
                        }
                        .buttonStyle(.bordered)
                    }
                }
            }
            if let error { Section { Text(error).foregroundStyle(.red) } }
        }
        .navigationTitle("Cards")
        .task { await load() }
    }

    private func load() async {
        guard let token = session.accessToken, let customerId = session.customerId else { return }
        do {
            accounts = try await HarborAPI.accounts(token: token, customerId: customerId)
            cards = try await HarborAPI.cards(token: token, customerId: customerId)
        } catch { self.error = error.localizedDescription }
    }

    private func issue() async {
        guard let token = session.accessToken, let customerId = session.customerId, let account = accounts.first else {
            error = "Open an account first"; return
        }
        busy = true; defer { busy = false }
        do {
            _ = try await HarborAPI.issueCard(token: token, body: .init(
                customerId: customerId, accountId: account.id, cardType: "DEBIT",
                cardNetwork: "VISA", dailyLimit: 1000.0, monthlyLimit: 10000.0
            ))
            await load()
        } catch { self.error = error.localizedDescription }
    }

    private func toggle(_ card: Card) async {
        guard let token = session.accessToken else { return }
        busy = true; defer { busy = false }
        do {
            if card.status == "FROZEN" { _ = try await HarborAPI.unfreezeCard(token: token, id: card.id) }
            else { _ = try await HarborAPI.freezeCard(token: token, id: card.id) }
            await load()
        } catch { self.error = error.localizedDescription }
    }
}
