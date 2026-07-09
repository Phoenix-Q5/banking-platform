import Foundation

enum HarborAPI {
    // MARK: - Customer banking
    static func accounts(token: String, customerId: String) async throws -> [Account] {
        try await APIClient.shared.request("GET", path: "/api/accounts?customerId=\(customerId)", token: token)
    }

    static func createAccount(token: String, customerId: String, currency: String = "USD") async throws -> Account {
        struct Body: Encodable { let customerId: String; let currency: String }
        return try await APIClient.shared.request("POST", path: "/api/accounts", token: token, body: Body(customerId: customerId, currency: currency))
    }

    static func transactions(token: String, accountId: String) async throws -> [Transaction] {
        try await APIClient.shared.request("GET", path: "/api/transactions?accountId=\(accountId)", token: token)
    }

    static func transfer(token: String, from: String, to: String, amount: Double, currency: String) async throws -> Transaction {
        struct Body: Encodable {
            let fromAccountId: String
            let toAccountId: String
            let amount: Double
            let currency: String
        }
        return try await APIClient.shared.request(
            "POST", path: "/api/transactions", token: token,
            body: Body(fromAccountId: from, toAccountId: to, amount: amount, currency: currency)
        )
    }

    static func payments(token: String, customerId: String) async throws -> [Payment] {
        try await APIClient.shared.request("GET", path: "/api/payments?customerId=\(customerId)", token: token)
    }

    struct CreatePaymentBody: Encodable {
        let customerId: String
        let fromAccountId: String
        let beneficiaryId: String?
        let paymentType: String
        let amount: Double
        let currency: String
        let reference: String?
        let description: String?
    }

    static func createPayment(token: String, body: CreatePaymentBody) async throws -> Payment {
        try await APIClient.shared.request("POST", path: "/api/payments", token: token, body: body)
    }

    static func beneficiaries(token: String, customerId: String) async throws -> [Beneficiary] {
        try await APIClient.shared.request("GET", path: "/api/payments/beneficiaries?customerId=\(customerId)", token: token)
    }

    struct CreateBeneficiaryBody: Encodable {
        let customerId: String
        let nickname: String
        let accountNumber: String
        let routingNumber: String?
        let bankName: String?
        let currency: String
    }

    static func createBeneficiary(token: String, body: CreateBeneficiaryBody) async throws -> Beneficiary {
        try await APIClient.shared.request("POST", path: "/api/payments/beneficiaries", token: token, body: body)
    }

    static func cards(token: String, customerId: String) async throws -> [Card] {
        try await APIClient.shared.request("GET", path: "/api/cards?customerId=\(customerId)", token: token)
    }

    struct IssueCardBody: Encodable {
        let customerId: String
        let accountId: String
        let cardType: String
        let cardNetwork: String
        let dailyLimit: Double
        let monthlyLimit: Double
    }

    static func issueCard(token: String, body: IssueCardBody) async throws -> Card {
        try await APIClient.shared.request("POST", path: "/api/cards", token: token, body: body)
    }

    static func freezeCard(token: String, id: String) async throws -> Card {
        try await APIClient.shared.request("POST", path: "/api/cards/\(id)/freeze", token: token)
    }

    static func unfreezeCard(token: String, id: String) async throws -> Card {
        try await APIClient.shared.request("POST", path: "/api/cards/\(id)/unfreeze", token: token)
    }

    static func loans(token: String, customerId: String) async throws -> [Loan] {
        try await APIClient.shared.request("GET", path: "/api/loans?customerId=\(customerId)", token: token)
    }

    static func allLoans(token: String) async throws -> [Loan] {
        try await APIClient.shared.request("GET", path: "/api/loans", token: token)
    }

    struct ApplyLoanBody: Encodable {
        let customerId: String
        let productCode: String
        let principal: Double
        let interestRate: Double
        let termMonths: Int
        let currency: String
        let purpose: String?
    }

    static func applyLoan(token: String, body: ApplyLoanBody) async throws -> Loan {
        try await APIClient.shared.request("POST", path: "/api/loans", token: token, body: body)
    }

    static func decideLoan(token: String, id: String, decision: String) async throws -> Loan {
        struct Body: Encodable { let decision: String }
        return try await APIClient.shared.request("POST", path: "/api/loans/\(id)/decision", token: token, body: Body(decision: decision))
    }

    static func customers(token: String, email: String? = nil) async throws -> [Customer] {
        if let email {
            let q = email.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? email
            return try await APIClient.shared.request("GET", path: "/api/customers?email=\(q)", token: token)
        }
        return try await APIClient.shared.request("GET", path: "/api/customers", token: token)
    }

    static func updateKyc(token: String, id: String, status: String) async throws -> Customer {
        struct Body: Encodable { let kycStatus: String }
        return try await APIClient.shared.request("POST", path: "/api/customers/\(id)/kyc", token: token, body: Body(kycStatus: status))
    }

    // MARK: - Notifications
    static func notifications(token: String, customerId: String) async throws -> [AppNotification] {
        try await APIClient.shared.request("GET", path: "/api/notifications?customerId=\(customerId)", token: token)
    }

    /// Admin/support service alerts are stored under a well-known audience customer id.
    static let serviceAlertAudienceId = "00000000-0000-4000-8000-0000000000aa"

    static func serviceNotifications(token: String) async throws -> [AppNotification] {
        try await notifications(token: token, customerId: serviceAlertAudienceId)
    }

    static func markRead(token: String, id: String) async throws -> AppNotification {
        try await APIClient.shared.request("POST", path: "/api/notifications/\(id)/read", token: token)
    }

    struct RegisterDeviceBody: Encodable {
        let customerId: String
        let platform: String
        let token: String
    }

    static func registerDevice(token: String, body: RegisterDeviceBody) async throws -> DeviceTokenResponse {
        try await APIClient.shared.request("POST", path: "/api/notifications/devices", token: token, body: body)
    }

    // MARK: - Ops agent (admin)
    static func incidents(token: String? = nil) async throws -> [ServiceIncident] {
        // ops-agent is open in the prototype; still pass token if present for future hardening
        try await APIClient.shared.request(
            "GET",
            path: "/api/agent/incidents",
            token: token,
            base: URL(string: ProcessInfo.processInfo.environment["HARBOR_OPS_BASE"] ?? "http://localhost:8085")!
        )
    }
}
