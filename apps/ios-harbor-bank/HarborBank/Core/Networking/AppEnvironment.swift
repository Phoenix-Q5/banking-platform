import Foundation

struct AppEnvironment {
    let apiBase: URL
    let keycloakBase: URL
    let realm: String
    let clientId: String

    static let current: AppEnvironment = {
        let env = ProcessInfo.processInfo.environment
        let api = env["HARBOR_API_BASE"] ?? "http://localhost:8080"
        let keycloak = env["HARBOR_KEYCLOAK_BASE"] ?? "http://localhost:8180"
        return AppEnvironment(
            apiBase: URL(string: api)!,
            keycloakBase: URL(string: keycloak)!,
            realm: env["HARBOR_REALM"] ?? "banking",
            clientId: env["HARBOR_CLIENT_ID"] ?? "banking-mobile"
        )
    }()
}
