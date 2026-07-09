import SwiftUI

struct AdminHomeView: View {
    @EnvironmentObject private var session: SessionStore
    @State private var customers: [Customer] = []
    @State private var loans: [Loan] = []
    @State private var query = "demo.customer@example.com"
    @State private var lookedUp: Customer?
    @State private var error: String?
    @State private var busy = false

    var body: some View {
        NavigationStack {
            List {
                Section("Contact center lookup") {
                    TextField("Customer email", text: $query)
                        .textInputAutocapitalization(.never)
                    Button("Search") { Task { await lookup() } }.disabled(busy)
                    if let c = lookedUp {
                        VStack(alignment: .leading) {
                            Text("\(c.firstName) \(c.lastName)").fontWeight(.semibold)
                            Text(c.email).font(.caption)
                            Text("KYC \(c.kycStatus) · \(c.status)").font(.caption).foregroundStyle(.secondary)
                        }
                    }
                }

                if session.isAdmin {
                    Section("KYC queue") {
                        ForEach(customers) { c in
                            HStack {
                                VStack(alignment: .leading) {
                                    Text("\(c.firstName) \(c.lastName)")
                                    Text(c.kycStatus).font(.caption).foregroundStyle(.secondary)
                                }
                                Spacer()
                                Button("Verify") { Task { await setKyc(c.id, "VERIFIED") } }
                                    .buttonStyle(.bordered)
                            }
                        }
                    }
                    Section("Loan pipeline") {
                        ForEach(loans) { l in
                            VStack(alignment: .leading, spacing: 6) {
                                Text("\(l.productCode) · \(MoneyFormat.string(l.principal, currency: l.currency))")
                                    .fontWeight(.semibold)
                                Text(l.status).font(.caption).foregroundStyle(.secondary)
                                HStack {
                                    Button("Review") { Task { await decide(l.id, "REVIEW") } }
                                    Button("Approve") { Task { await decide(l.id, "APPROVE") } }
                                    Button("Activate") { Task { await decide(l.id, "ACTIVATE") } }
                                }
                                .font(.caption)
                                .buttonStyle(.bordered)
                            }
                        }
                    }
                }

                Section("Service alerts") {
                    NavigationLink("Open Alerts inbox") { AlertsView() }
                    Text("Ops-agent incidents and Alertmanager-driven service alerts appear under Alerts → Service.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                if let error { Section { Text(error).foregroundStyle(.red) } }

                Section {
                    Button("Sign out", role: .destructive) { session.logout() }
                }
            }
            .navigationTitle(session.isAdmin ? "Admin" : "Support")
            .task { await loadAdmin() }
        }
    }

    private func loadAdmin() async {
        guard session.isAdmin, let token = session.accessToken else { return }
        do {
            customers = try await HarborAPI.customers(token: token)
            loans = try await HarborAPI.allLoans(token: token)
        } catch { self.error = error.localizedDescription }
    }

    private func lookup() async {
        guard let token = session.accessToken else { return }
        busy = true; defer { busy = false }
        do {
            lookedUp = try await HarborAPI.customers(token: token, email: query).first
            if lookedUp == nil { error = "No customer found" }
        } catch { self.error = error.localizedDescription }
    }

    private func setKyc(_ id: String, _ status: String) async {
        guard let token = session.accessToken else { return }
        _ = try? await HarborAPI.updateKyc(token: token, id: id, status: status)
        await loadAdmin()
    }

    private func decide(_ id: String, _ decision: String) async {
        guard let token = session.accessToken else { return }
        _ = try? await HarborAPI.decideLoan(token: token, id: id, decision: decision)
        await loadAdmin()
    }
}
