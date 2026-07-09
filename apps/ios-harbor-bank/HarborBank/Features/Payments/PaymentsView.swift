import SwiftUI

struct PaymentsView: View {
    @EnvironmentObject private var session: SessionStore
    @State private var accounts: [Account] = []
    @State private var beneficiaries: [Beneficiary] = []
    @State private var payments: [Payment] = []
    @State private var type = "ACH"
    @State private var amount = "50.00"
    @State private var reference = ""
    @State private var selectedBeneficiary: String = ""
    @State private var nickname = ""
    @State private var accountNumber = ""
    @State private var error: String?
    @State private var busy = false

    var body: some View {
        NavigationStack {
            List {
                Section("Send payment") {
                    Picker("Type", selection: $type) {
                        Text("ACH").tag("ACH")
                        Text("WIRE").tag("WIRE")
                        Text("BILL_PAY").tag("BILL_PAY")
                    }
                    Picker("Beneficiary", selection: $selectedBeneficiary) {
                        Text("None").tag("")
                        ForEach(beneficiaries) { b in
                            Text(b.nickname).tag(b.id)
                        }
                    }
                    TextField("Amount", text: $amount).keyboardType(.decimalPad)
                    TextField("Reference", text: $reference)
                    Button("Submit") { Task { await pay() } }.disabled(busy)
                }
                Section("Add beneficiary") {
                    TextField("Nickname", text: $nickname)
                    TextField("Account number", text: $accountNumber)
                    Button("Save payee") { Task { await addPayee() } }.disabled(busy)
                }
                Section("History") {
                    ForEach(payments) { p in
                        VStack(alignment: .leading) {
                            Text("\(p.paymentType) · \(MoneyFormat.string(p.amount, currency: p.currency))").fontWeight(.semibold)
                            Text(p.status).font(.caption).foregroundStyle(.secondary)
                        }
                    }
                }
                if let error { Section { Text(error).foregroundStyle(.red) } }
            }
            .navigationTitle("Pay")
            .task { await load() }
        }
    }

    private func load() async {
        guard let token = session.accessToken, let customerId = session.customerId else { return }
        do {
            accounts = try await HarborAPI.accounts(token: token, customerId: customerId)
            beneficiaries = try await HarborAPI.beneficiaries(token: token, customerId: customerId)
            payments = try await HarborAPI.payments(token: token, customerId: customerId)
            if selectedBeneficiary.isEmpty { selectedBeneficiary = beneficiaries.first?.id ?? "" }
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func pay() async {
        guard let token = session.accessToken, let customerId = session.customerId,
              let from = accounts.first, let value = Double(amount) else { return }
        busy = true
        defer { busy = false }
        do {
            _ = try await HarborAPI.createPayment(token: token, body: .init(
                customerId: customerId,
                fromAccountId: from.id,
                beneficiaryId: selectedBeneficiary.isEmpty ? nil : selectedBeneficiary,
                paymentType: type,
                amount: value,
                currency: "USD",
                reference: reference,
                description: "\(type) payment"
            ))
            await load()
        } catch { self.error = error.localizedDescription }
    }

    private func addPayee() async {
        guard let token = session.accessToken, let customerId = session.customerId else { return }
        busy = true
        defer { busy = false }
        do {
            _ = try await HarborAPI.createBeneficiary(token: token, body: .init(
                customerId: customerId, nickname: nickname, accountNumber: accountNumber,
                routingNumber: nil, bankName: nil, currency: "USD"
            ))
            nickname = ""; accountNumber = ""
            await load()
        } catch { self.error = error.localizedDescription }
    }
}
