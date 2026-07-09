import Foundation

enum AuthService {
    struct TokenResponse: Decodable {
        let accessToken: String
        let refreshToken: String?
        let expiresIn: Int?

        enum CodingKeys: String, CodingKey {
            case accessToken = "access_token"
            case refreshToken = "refresh_token"
            case expiresIn = "expires_in"
        }
    }

    static func login(username: String, password: String) async throws -> TokenResponse {
        let env = AppEnvironment.current
        let url = env.keycloakBase
            .appendingPathComponent("realms")
            .appendingPathComponent(env.realm)
            .appendingPathComponent("protocol/openid-connect/token")

        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        let body = [
            "client_id": env.clientId,
            "grant_type": "password",
            "username": username,
            "password": password,
            "scope": "openid profile email"
        ]
        req.httpBody = body
            .map { "\($0.key)=\($0.value.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? $0.value)" }
            .joined(separator: "&")
            .data(using: .utf8)

        let (data, response) = try await URLSession.shared.data(for: req)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            let text = String(data: data, encoding: .utf8) ?? "Login failed"
            throw APIError.http((response as? HTTPURLResponse)?.statusCode ?? 400, text)
        }
        return try JSONDecoder().decode(TokenResponse.self, from: data)
    }
}
