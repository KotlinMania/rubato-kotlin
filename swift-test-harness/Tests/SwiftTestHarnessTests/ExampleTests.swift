#if canImport(Testing)
import Testing
import Rubato

@Suite("Rubato Swift Export Tests")
struct RubatoExportTests {
    @Test("Rubato swift module imported cleanly")
    func testSwiftModuleLoads() throws {
        let fc = FrameCount(inputFrames: 512, outputFrames: 1024)
        #expect(fc.inputFrames == 512 && fc.outputFrames == 1024, "Rubato swift module imported cleanly")
    }
}
#elseif canImport(XCTest)
import XCTest
import Rubato

final class RubatoExportTests: XCTestCase {
    func testSwiftModuleLoads() throws {
        let fc = FrameCount(inputFrames: 512, outputFrames: 1024)
        XCTAssertEqual(fc.inputFrames, 512)
        XCTAssertEqual(fc.outputFrames, 1024)
    }
}
#endif
