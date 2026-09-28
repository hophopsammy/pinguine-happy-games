import SwiftUI
import SharedLogic
import UniformTypeIdentifiers

extension Int32 {
    var int: Int { Int(self) }
}

extension Int64 {
    /// Kotlin timestamps are epoch milliseconds.
    var date: Date { Date(timeIntervalSince1970: TimeInterval(self) / 1000) }
}

extension UTType {
    static let pinguineBackup = UTType(exportedAs: "com.pinguine.spiele.backup", conformingTo: .json)
}

extension GameType {
    /// Game names are brand names and stay the same in every language.
    var title: String {
        switch self {
        case .skyjo: "Skyjo"
        case .biberbande: "Biberbande"
        case .wizard: "Wizard"
        default: name
        }
    }

    var symbol: String {
        switch self {
        case .skyjo: "square.grid.3x3.fill"
        case .biberbande: "pawprint.fill"
        default: "wand.and.stars"
        }
    }

    var tint: Color {
        switch self {
        case .skyjo: Color(red: 0.13, green: 0.52, blue: 0.83)
        case .biberbande: Color(red: 0.62, green: 0.39, blue: 0.19)
        default: Color(red: 0.40, green: 0.27, blue: 0.70)
        }
    }

    var gradient: LinearGradient {
        LinearGradient(colors: [tint, tint.mix(with: .black, by: 0.35)], startPoint: .topLeading, endPoint: .bottomTrailing)
    }

    var rules: LocalizedStringKey {
        switch self {
        case .skyjo: "Lowest total wins. The game ends when someone reaches 100 points."
        case .biberbande: "Lowest total wins. One round per player."
        default: "Highest total wins. 60 cards divided by the number of players gives the rounds."
        }
    }
}

extension Color {
    /// A color that follows light and dark mode.
    init(light: Color, dark: Color) {
        self.init(UIColor { traits in
            traits.userInterfaceStyle == .dark ? UIColor(dark) : UIColor(light)
        })
    }
}

/// "+5", "−3", "0": how round points are written on the pads.
func signed(_ value: Int32) -> String {
    if value > 0 { return "+\(value)" }
    if value < 0 { return "−\(-value)" }
    return "0"
}

/// Names joined the local way: "Anna, Ben and Carla".
func joined(_ names: [String]) -> String {
    ListFormatter.localizedString(byJoining: names)
}
