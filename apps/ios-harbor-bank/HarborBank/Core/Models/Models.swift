import Foundation

struct Customer: Codable, Identifiable, Hashable {
    let id: String
    let externalUserId: String?
    let email: String
    let firstName: String
    let lastName: String
    let phone: String?
    let kycStatus: String
    let status: String
}

struct Account: Codable, Identifiable, Hashable {
    let id: String
    let accountNumber: String
    let customerId: String
    let balance: Double
    let currency: String
    let status: String
    let createdAt: Date?
}

struct Transaction: Codable, Identifiable, Hashable {
    let id: String
    let fromAccountId: String
    let toAccountId: String
    let amount: Double
    let currency: String
    let status: String
    let failureReason: String?
    let createdAt: Date?
}

struct Payment: Codable, Identifiable, Hashable {
    let id: String
    let customerId: String
    let fromAccountId: String
    let beneficiaryId: String?
    let paymentType: String
    let amount: Double
    let currency: String
    let status: String
    let reference: String?
    let createdAt: Date?
}

struct Beneficiary: Codable, Identifiable, Hashable {
    let id: String
    let customerId: String
    let nickname: String
    let accountNumber: String
    let routingNumber: String?
    let bankName: String?
    let currency: String
    let status: String
}

struct Card: Codable, Identifiable, Hashable {
    let id: String
    let customerId: String
    let accountId: String
    let cardNumberLast4: String
    let cardNetwork: String
    let cardType: String
    let status: String
    let dailyLimit: Double
    let monthlyLimit: Double
    let expiresOn: String?
}

struct Loan: Codable, Identifiable, Hashable {
    let id: String
    let customerId: String
    let productCode: String
    let principal: Double
    let interestRate: Double
    let termMonths: Int
    let monthlyPayment: Double
    let outstandingBalance: Double
    let currency: String
    let status: String
    let purpose: String?
}

struct AppNotification: Codable, Identifiable, Hashable {
    let id: String
    let customerId: String
    let channel: String
    let category: String
    let title: String
    let body: String
    let status: String
    let readAt: Date?
    let eventId: String?
    let eventType: String?
    let createdAt: Date?
}

struct DeviceTokenResponse: Codable {
    let id: String
    let customerId: String
    let platform: String
    let token: String
    let active: Bool
}

struct ServiceIncident: Codable, Identifiable, Hashable {
    let id: String
    let title: String
    let summary: String?
    let severity: String
    let status: String
    let category: String?
    let affectedService: String?
    let rootCauseHypothesis: String?
    let createdAt: Date?
    let updatedAt: Date?
}

struct MoneyFormat {
    static func string(_ amount: Double, currency: String = "USD") -> String {
        let f = NumberFormatter()
        f.numberStyle = .currency
        f.currencyCode = currency
        return f.string(from: NSNumber(value: amount)) ?? "\(amount) \(currency)"
    }
}
