import SwiftUI

struct LoansView: View {
    @EnvironmentObject private var session: SessionStore
    @State private var loans: [Loan] = []
    @State private var principal = "5000"
    @State private var rate = "8.5"
    @State private var term = "36"
    @State private var purpose = "Home improvement"
    @State private var product = "PERSONAL_UNSECURED"
    @State private var error: String?
    @State private var busy = false

    var body: some View {
        List {
            Section("Apply") {
                Picker("Product", selection: $product) {
                    Text("Personal").tag("PERSONAL_UNSECURED")
                    Text("Auto").tag("AUTO")
                    Text("Home").tag("HOME_IMPROVEMENT")
                }
                TextField("Principal", text: $principal).keyboardType(.decimalPad)
                TextField("APR %", text: $rate).keyboardType(.decimalPad)
                TextField("Term months", text: $term).keyboardType(.numberPad)
                TextField("Purpose", text: $purpose)
                Button("Submit application") { Task { await apply() } }.disabled(busy)
            }
            Section("Your loans") {
                ForEach(loans) { l in
                    VStack(alignment: .leading) {
                        Text(l.productCode).fontWeight(.semibold)
                        Text("\(MoneyFormat.string(l.principal, currency: l.currency)) · \(l.status)")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                }
            }
            if let error { Section { Text(error).foregroundStyle(.red) } }
        }
        .navigationTitle("Loans")
        .task { await load() }
    }

    private func load() async {
        guard let token = session.accessToken, let customerId = session.customerId else { return }
        loans = (try? await HarborAPI.loans(token: token, customerId: customerId)) ?? []
    }

    private func apply() async {
        guard let token = session.accessToken, let customerId = session.customerId,
              let p = Double(principal), let r = Double(rate), let t = Int(term) else { return }
        busy = true; defer { busy = false }
        do {
            _ = try await HarborAPI.applyLoan(token: token, body: .init(
                customerId: customerId, productCode: product, principal: p,
                interestRate: r, termMonths: t, currency: "USD", purpose: purpose
            ))
            await load()
        } catch { self.error = error.localizedDescription }
    }
}
