#if canImport(Testing)
import Testing
import Rubato

@Suite("Rubato Swift Export Tests")
struct RubatoExportTests {
    @Test("Rubato swift module imported cleanly")
    func testSwiftModuleLoads() throws {
        #expect(Bool(true), "Rubato swift module imported cleanly")
    }
}
#elseif canImport(XCTest)
import XCTest
import Rubato

final class RubatoExportTests: XCTestCase {
    func testSwiftModuleLoads() throws {
        XCTAssertTrue(true, "Rubato swift module imported cleanly")
    }
}
#endif
