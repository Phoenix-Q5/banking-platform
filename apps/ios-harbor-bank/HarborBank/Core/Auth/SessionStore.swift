import Foundation
import Combine

@MainActor
final class SessionStore: ObservableObject {
    @Published private(set) var accessToken: String?
    @Published private(set) var username: String = ""
    @Published private(set) var displayName: String = ""
    @Published private(set) var email: String = ""
    @Published private(set) var roles: [String] = []
    @Published private(set) var customerId: String?
    @Published var lastError: String?

    private let defaults = UserDefaults.standard
    private let storageKey = "harbor.session.v1"

    var isAuthenticated: Bool { accessToken != nil }
    var isCustomer: Bool { roles.contains("CUSTOMER") }
    var isAdmin: Bool { roles.contains("ADMIN") }
    var isSupport: Bool { roles.contains("SUPPORT") || roles.contains("ADMIN") }

    init() {
        restore()
    }

    func login(username: String, password: String) async {
        lastError = nil
        do {
            let tokens = try await AuthService.login(username: username, password: password)
            let claims = JWT.decode(tokens.accessToken)
            let roles = (claims?.realmAccess?.roles ?? []).filter { ["CUSTOMER", "ADMIN", "SUPPORT"].contains($0) }
            var customerId: String?
            if roles.contains("CUSTOMER") {
                let email = claims?.email ?? "\(username)@example.com"
                let matches: [Customer] = try await APIClient.shared.request(
                    "GET",
                    path: "/api/customers?email=\(email.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? email)",
                    token: tokens.accessToken
                )
                customerId = matches.first?.id
                if customerId == nil {
                    let byUser: [Customer] = try await APIClient.shared.request(
                        "GET",
                        path: "/api/customers?externalUserId=\(username)",
                        token: tokens.accessToken
                    )
                    customerId = byUser.first?.id
                }
            }
            self.accessToken = tokens.accessToken
            self.username = claims?.preferredUsername ?? username
            self.email = claims?.email ?? ""
            self.displayName = [claims?.givenName, claims?.familyName].compactMap { $0 }.joined(separator: " ")
            if self.displayName.isEmpty { self.displayName = self.username }
            self.roles = roles
            self.customerId = customerId
            persist()
            await PushRegistrationService.shared.syncIfPossible()
        } catch {
            lastError = error.localizedDescription
        }
    }

    func logout() {
        accessToken = nil
        username = ""
        displayName = ""
        email = ""
        roles = []
        customerId = nil
        defaults.removeObject(forKey: storageKey)
    }

    private func persist() {
        let payload: [String: Any] = [
            "accessToken": accessToken as Any,
            "username": username,
            "displayName": displayName,
            "email": email,
            "roles": roles,
            "customerId": customerId as Any
        ]
        defaults.set(payload, forKey: storageKey)
    }

    private func restore() {
        guard let payload = defaults.dictionary(forKey: storageKey) else { return }
        accessToken = payload["accessToken"] as? String
        username = payload["username"] as? String ?? ""
        displayName = payload["displayName"] as? String ?? ""
        email = payload["email"] as? String ?? ""
        roles = payload["roles"] as? [String] ?? []
        customerId = payload["customerId"] as? String
    }
}
