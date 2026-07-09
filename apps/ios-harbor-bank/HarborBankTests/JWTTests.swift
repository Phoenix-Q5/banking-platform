
import XCTest
@testable import HarborBank

final class JWTTests: XCTestCase {
    func testDecodeRoles() {
        // header.payload.sig — payload is {"preferred_username":"demo","realm_access":{"roles":["CUSTOMER","ADMIN"]}}
        let payload = Data(#"{"preferred_username":"demo","realm_access":{"roles":["CUSTOMER","ADMIN"]}}"#.utf8).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
        let token = "aaa.\(payload).bbb"
        let claims = JWT.decode(token)
        XCTAssertEqual(claims?.preferredUsername, "demo")
        XCTAssertEqual(claims?.realmAccess?.roles?.sorted(), ["ADMIN", "CUSTOMER"])
    }
}
