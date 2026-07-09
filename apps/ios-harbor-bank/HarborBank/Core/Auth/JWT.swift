import Foundation

enum JWT {
    struct Claims: Decodable {
        let preferredUsername: String?
        let email: String?
        let givenName: String?
        let familyName: String?
        let realmAccess: RealmAccess?

        enum CodingKeys: String, CodingKey {
            case preferredUsername = "preferred_username"
            case email
            case givenName = "given_name"
            case familyName = "family_name"
            case realmAccess = "realm_access"
        }
    }

    struct RealmAccess: Decodable {
        let roles: [String]?
    }

    static func decode(_ token: String) -> Claims? {
        let parts = token.split(separator: ".")
        guard parts.count >= 2 else { return nil }
        var base64 = String(parts[1])
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        while base64.count % 4 != 0 { base64.append("=") }
        guard let data = Data(base64Encoded: base64) else { return nil }
        return try? JSONDecoder().decode(Claims.self, from: data)
    }
}
