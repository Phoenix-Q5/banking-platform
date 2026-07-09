import SwiftUI

struct HomeView: View {
    @EnvironmentObject private var session: SessionStore
    @State private var accounts: [Account] = []
    @State private var transactions: [Transaction] = []
    @State private var error: String?
    @State private var busy = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    Text("Good day, \(session.displayName)")
                        .font(.system(size: 28, weight: .bold, design: .serif))
                    Text("Balances and recent transfers")
                        .foregroundStyle(HarborTheme.muted)

                    if let error { Text(error).foregroundStyle(.red).font(.footnote) }

                    VStack(alignment: .leading, spacing: 12) {
                        Text("TOTAL AVAILABLE").font(.caption.weight(.semibold)).foregroundStyle(HarborTheme.seaDeep)
                        Text(MoneyFormat.string(accounts.reduce(0.0) { $0 + $1.balance }))
                            .font(.system(size: 34, weight: .bold, design: .serif))
                        ForEach(accounts) { a in
                            HStack {
                                VStack(alignment: .leading) {
                                    Text(a.accountNumber).fontWeight(.semibold)
                                    Text(a.status).font(.caption).foregroundStyle(HarborTheme.muted)
                                }
                                Spacer()
                                Text(MoneyFormat.string(a.balance, currency: a.currency))
                            }
                            .padding(.vertical, 4)
                            Divider()
                        }
                        Button("Open account") { Task { await openAccount() } }
                            .buttonStyle(.borderedProminent)
                            .tint(HarborTheme.sea)
                            .disabled(busy)
                    }
                    .padding()
                    .background(.white)
                    .clipShape(RoundedRectangle(cornerRadius: 8))

                    VStack(alignment: .leading, spacing: 10) {
                        Text("RECENT TRANSFERS").font(.caption.weight(.semibold)).foregroundStyle(HarborTheme.seaDeep)
                        if transactions.isEmpty {
                            Text("No transfers yet.").foregroundStyle(HarborTheme.muted)
                        } else {
                            ForEach(transactions.prefix(8)) { t in
                                HStack {
                                    VStack(alignment: .leading) {
                                        Text(MoneyFormat.string(t.amount, currency: t.currency)).fontWeight(.semibold)
                                        Text(t.status).font(.caption).foregroundStyle(HarborTheme.muted)
                                    }
                                    Spacer()
                                    Text(t.createdAt?.formatted(date: .abbreviated, time: .shortened) ?? "")
                                        .font(.caption2)
                                        .foregroundStyle(HarborTheme.muted)
                                }
                                Divider()
                            }
                        }
                    }
                    .padding()
                    .background(.white)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                }
                .padding()
            }
            .background(HarborTheme.paper.ignoresSafeArea())
            .navigationTitle("Harbor Bank")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Sign out") { session.logout() }
                }
            }
            .task { await load() }
            .refreshable { await load() }
        }
    }

    private func load() async {
        guard let token = session.accessToken, let customerId = session.customerId else { return }
        do {
            accounts = try await HarborAPI.accounts(token: token, customerId: customerId)
            if let first = accounts.first {
                transactions = try await HarborAPI.transactions(token: token, accountId: first.id)
            }
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func openAccount() async {
        guard let token = session.accessToken, let customerId = session.customerId else { return }
        busy = true
        defer { busy = false }
        do {
            _ = try await HarborAPI.createAccount(token: token, customerId: customerId)
            await load()
        } catch {
            self.error = error.localizedDescription
        }
    }
}
