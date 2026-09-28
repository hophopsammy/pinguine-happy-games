import SwiftUI
import SharedLogic

/// The score pad: players as columns, rounds as rows, styled after each game's paper pad.
struct PaperSheet: View {
    let sheet: ScoreSheetModel
    let availableWidth: CGFloat
    let onTapRow: (SheetRow) -> Void

    private var style: PadStyle { PadStyle(type: sheet.type) }

    private var columnWidth: CGFloat {
        let count = CGFloat(max(sheet.columns.count, 1))
        let fitting = (availableWidth - style.labelWidth - style.framePadding * 2) / count
        return max(style.minColumnWidth, fitting.rounded(.down))
    }

    var body: some View {
        VStack(spacing: 0) {
            if sheet.type == .biberbande { BiberbandeTitle() }
            VStack(spacing: 0) {
                header
                ForEach(sheet.rows, id: \.roundIndex) { row in
                    dataRow(row)
                }
                if sheet.type == .biberbande {
                    // The pad leaves a gap before the final score.
                    style.paper.frame(height: 10)
                }
                totals
            }
            .overlay { if sheet.type == .skyjo { SkyjoWatermark() } }
            .padding(style.framePadding)
            .background(style.frame)
            if sheet.type == .wizard { WizardFooter() }
        }
        .background(style.frame)
        .clipShape(RoundedRectangle(cornerRadius: 10))
        .shadow(color: .black.opacity(0.12), radius: 6, y: 2)
    }

    // MARK: Rows

    private var header: some View {
        HStack(spacing: 0) {
            labelCell {
                if sheet.type == .biberbande {
                    Text("Players:").font(.caption2.weight(.semibold)).lineLimit(1).minimumScaleFactor(0.5).padding(.horizontal, 2)
                }
            }
            .background(style.labelBackground)
            ForEach(Array(sheet.columns.enumerated()), id: \.element.playerId) { index, column in
                VStack(spacing: 1) {
                    Text(column.name)
                        .font(.subheadline.weight(.semibold))
                        .lineLimit(1)
                        .minimumScaleFactor(0.6)
                    if column.isWinner {
                        Image(systemName: "trophy.fill").font(.caption2).foregroundStyle(.orange)
                    } else if column.isLeader {
                        Image(systemName: "star.fill").font(.caption2).foregroundStyle(.orange.opacity(0.7))
                    }
                }
                .padding(.horizontal, 4)
                .frame(width: columnWidth, height: style.headerHeight)
                .background(style.headerBackground(column: index))
                .overlay(gridLine)
            }
        }
        .foregroundStyle(style.ink)
    }

    private func dataRow(_ row: SheetRow) -> some View {
        HStack(spacing: 0) {
            labelCell {
                Text(verbatim: "\(row.roundNumber)")
                    .font(.subheadline.weight(.bold))
                    .monospacedDigit()
                    .foregroundStyle(style.labelInk)
            }
            .background(style.labelBackground)
            ForEach(Array(row.cells.enumerated()), id: \.element.playerId) { index, cell in
                Color.clear
                    .frame(width: columnWidth, height: style.rowHeight)
                    .overlay { cellView(cell, row: row) }
                    .background {
                        style.cellBackground(column: index, row: row.roundIndex.int)
                        highlight(row)
                    }
                    .overlay(alignment: .topLeading) {
                        if row.dealerSeat.int == index && (row.state == .next || row.state == .bidsPlaced) {
                            Image(systemName: "suit.spade.fill")
                                .font(.system(size: 8))
                                .foregroundStyle(style.ink.opacity(0.6))
                                .padding(3)
                                .accessibilityLabel("Dealer")
                        }
                    }
                    .overlay(gridLine)
            }
        }
        .foregroundStyle(style.ink)
        .contentShape(Rectangle())
        .onTapGesture { onTapRow(row) }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(row.canEdit || row.state == .next ? .isButton : [])
    }

    private var totals: some View {
        HStack(spacing: 0) {
            labelCell {
                Text(sheet.type == .biberbande ? LocalizedStringKey("Final") : "Σ")
                    .font(.caption.weight(.bold))
                    .lineLimit(1)
                    .minimumScaleFactor(0.5)
                    .padding(.horizontal, 2)
                    .foregroundStyle(style.labelInk)
            }
            .background(style.labelBackground)
            ForEach(Array(sheet.columns.enumerated()), id: \.element.playerId) { index, column in
                Text(verbatim: "\(column.total)")
                    .font(.system(.title3, design: .rounded).weight(.bold))
                    .monospacedDigit()
                    .foregroundStyle(totalColor(column))
                    .padding(.horizontal, 6)
                    .overlay {
                        if column.isWinner {
                            Ellipse()
                                .stroke(style.ink, lineWidth: 1.5)
                                .padding(-5)
                                .accessibilityLabel("Winner")
                        }
                    }
                    .frame(width: columnWidth, height: style.rowHeight + 8)
                    .background(style.totalsBackground)
                    .overlay(gridLine)
            }
        }
    }

    // MARK: Cells

    @ViewBuilder
    private func cellView(_ cell: SheetCell, row: SheetRow) -> some View {
        switch sheet.type {
        case .wizard: wizardCell(cell)
        case .skyjo: skyjoCell(cell)
        default: biberbandeCell(cell)
        }
    }

    @ViewBuilder
    private func skyjoCell(_ cell: SheetCell) -> some View {
        if cell.hasScore {
            VStack(spacing: 0) {
                Text(verbatim: "\(cell.runningTotal)")
                    .font(.system(.body, design: .rounded).weight(.semibold))
                    .monospacedDigit()
                HStack(spacing: 2) {
                    if cell.isRoundEnder { Image(systemName: "flag.fill") }
                    Text(signed(cell.roundScore))
                    if cell.isDoubled { Text(verbatim: "×2").foregroundStyle(.orange) }
                }
                .font(.caption2.monospacedDigit())
                .foregroundStyle(style.ink.opacity(0.65))
            }
        }
    }

    @ViewBuilder
    private func biberbandeCell(_ cell: SheetCell) -> some View {
        if cell.hasScore {
            Text(verbatim: "\(cell.roundScore)")
                .font(.system(.title3, design: .rounded))
                .monospacedDigit()
        }
    }

    private func wizardCell(_ cell: SheetCell) -> some View {
        HStack(spacing: 0) {
            Color.clear
                .overlay {
                    if cell.hasScore {
                        Text(verbatim: "\(cell.runningTotal)")
                            .font(.system(.body, design: .rounded).weight(.semibold))
                            .monospacedDigit()
                            .minimumScaleFactor(0.7)
                    }
                }
                .background(cell.hasScore ? (cell.madeBid ? Color.green : Color.red).opacity(0.13) : .clear)
            Rectangle().fill(style.line).frame(width: 0.5)
            Color.clear
                .frame(width: style.bidWidth)
                .overlay {
                    if cell.hasBid {
                        Text(verbatim: "\(cell.bid)")
                            .font(.caption.weight(.semibold))
                            .monospacedDigit()
                    }
                }
        }
        .accessibilityElement(children: .combine)
    }

    // MARK: Helpers

    private func labelCell<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        Color.clear
            .frame(width: style.labelWidth)
            .frame(maxHeight: .infinity)
            .overlay { content() }
            .overlay(gridLine)
    }

    private var gridLine: some View {
        Rectangle().stroke(style.line, lineWidth: 0.5)
    }

    private func highlight(_ row: SheetRow) -> Color {
        switch row.state {
        case .next, .bidsPlaced: sheet.type.tint.opacity(0.14)
        default: .clear
        }
    }

    private func totalColor(_ column: SheetColumn) -> Color {
        if sheet.type == .skyjo && column.total >= sheet.endScore { return .red }
        return style.ink
    }
}

/// Colors and sizes of each paper pad.
struct PadStyle {
    let type: GameType

    var minColumnWidth: CGFloat { type == .wizard ? 70 : 58 }
    var labelWidth: CGFloat { type == .biberbande ? 64 : 34 }
    var bidWidth: CGFloat { 24 }
    var rowHeight: CGFloat { type == .wizard ? 32 : 40 }
    var headerHeight: CGFloat { 44 }
    var framePadding: CGFloat { type == .wizard ? 10 : 0 }

    var paper: Color { Color(light: Color(white: 0.99), dark: Color(white: 0.16)) }
    var shade: Color { Color(light: Color(white: 0.88), dark: Color(white: 0.24)) }
    var ink: Color { Color(light: Color(white: 0.12), dark: Color(white: 0.92)) }
    var line: Color { Color(light: Color(white: 0.2).opacity(0.6), dark: Color(white: 0.6).opacity(0.5)) }

    var frame: Color {
        type == .wizard ? Color(white: 0.2) : paper
    }

    var labelBackground: Color {
        switch type {
        case .skyjo: Color(white: 0.32)
        case .wizard: Color(white: 0.2)
        default: paper
        }
    }

    var labelInk: Color {
        type == .biberbande ? ink : .white
    }

    var totalsBackground: Color { type == .biberbande ? paper : shade.opacity(0.6) }

    func headerBackground(column: Int) -> Color {
        switch type {
        case .skyjo: column.isMultiple(of: 2) ? shade.opacity(0.7) : shade
        default: paper
        }
    }

    func cellBackground(column: Int, row: Int) -> Color {
        switch type {
        case .skyjo: column.isMultiple(of: 2) ? paper : shade
        case .wizard: row.isMultiple(of: 2) ? paper : shade.opacity(0.8)
        default: paper
        }
    }
}

private struct SkyjoWatermark: View {
    var body: some View {
        Text(verbatim: "SKYJO")
            .font(.system(size: 88, weight: .black, design: .serif))
            .foregroundStyle(.gray.opacity(0.13))
            .rotationEffect(.degrees(-28))
            .allowsHitTesting(false)
            .accessibilityHidden(true)
    }
}

private struct BiberbandeTitle: View {
    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "pawprint.fill")
            Text(verbatim: "BIBERBANDE")
                .font(.system(.title2, design: .rounded).weight(.black))
                .kerning(1.5)
            Image(systemName: "pawprint.fill")
        }
        .foregroundStyle(Color(light: Color(white: 0.15), dark: Color(white: 0.9)))
        .padding(.vertical, 10)
        .frame(maxWidth: .infinity)
        .accessibilityHidden(true)
    }
}

private struct WizardFooter: View {
    var body: some View {
        HStack {
            Spacer()
            Text(verbatim: "wizard")
                .font(.system(size: 26, weight: .semibold, design: .serif))
                .italic()
                .foregroundStyle(.white.opacity(0.85))
        }
        .padding(.horizontal, 14)
        .padding(.bottom, 8)
        .accessibilityHidden(true)
    }
}
