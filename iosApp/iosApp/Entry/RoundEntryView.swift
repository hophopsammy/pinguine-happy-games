import SwiftUI
import SharedLogic

/// Entering or correcting one round. Everything typed goes straight to the Kotlin store, which also
/// computes the previews (Skyjo doubling, Wizard scores) and decides whether the round can be saved.
struct RoundEntryView: View {
    @Environment(AppModel.self) private var model
    @State private var texts: [String: String] = [:]
    @State private var confirmMismatch = false
    @FocusState private var focusedPlayer: String?

    var body: some View {
        NavigationStack {
            if let entry = Selectors.shared.roundEntry(state: model.state) {
                content(entry)
            }
        }
        .interactiveDismissDisabled(texts.values.contains { !$0.isEmpty })
    }

    private func content(_ entry: RoundEntryModel) -> some View {
        List {
            if entry.type == .wizard {
                wizardSections(entry)
            } else {
                pointsSection(entry)
            }
        }
        .navigationTitle(title(entry))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button("Cancel") { model.dispatch(Actions.shared.cancelRoundEntry()) }
            }
            ToolbarItem(placement: .confirmationAction) {
                Button("Save") { save(entry) }
                    .fontWeight(.semibold)
                    .disabled(!entry.canSubmit)
            }
        }
        .confirmationDialog("Tricks don't add up", isPresented: $confirmMismatch, titleVisibility: .visible) {
            Button("Save anyway") { model.dispatch(Actions.shared.submitRoundEntry(allowTrickMismatch: true)) }
            Button("Fix tricks", role: .cancel) {}
        } message: {
            Text("The tricks add up to \(entry.trickSum.int), but this round needs \(entry.cards.int).")
        }
        .onAppear {
            if entry.phase == .points {
                for row in entry.rows where row.hasValue { texts[row.playerId] = "\(row.value)" }
            }
        }
        .task {
            guard entry.phase == .points else { return }
            // Focusing before the sheet finished presenting is ignored.
            try? await Task.sleep(for: .milliseconds(450))
            focusedPlayer = entry.rows.first { !$0.hasValue }?.playerId
        }
    }

    private func title(_ entry: RoundEntryModel) -> LocalizedStringKey {
        let round = entry.roundNumber.int
        switch entry.phase {
        case .bids: return "Bids · Round \(round)"
        case .tricks: return "Tricks · Round \(round)"
        default: return entry.isEditing ? "Correct round \(round)" : "Round \(round)"
        }
    }

    private func save(_ entry: RoundEntryModel) {
        if entry.needsMismatchConfirmation {
            confirmMismatch = true
        } else {
            model.dispatch(Actions.shared.submitRoundEntry(allowTrickMismatch: false))
        }
    }

    // MARK: Skyjo and Biberbande

    private func pointsSection(_ entry: RoundEntryModel) -> some View {
        Section {
            ForEach(entry.rows, id: \.playerId) { row in
                HStack(spacing: 10) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(row.name).font(.headline)
                        Text("Total so far: \(row.totalBefore.int)")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    .accessibilityElement(children: .combine)
                    Spacer()
                    if entry.type == .skyjo {
                        Button {
                            model.dispatch(Actions.shared.setRoundEnder(playerId: row.isRoundEnder ? nil : row.playerId))
                        } label: {
                            Image(systemName: row.isRoundEnder ? "flag.fill" : "flag")
                                .foregroundStyle(row.isRoundEnder ? entry.type.tint : .secondary)
                        }
                        .buttonStyle(.borderless)
                        .accessibilityLabel(row.isRoundEnder ? "\(row.name) ended the round" : "Mark \(row.name) as the player who ended the round")
                    }
                    if row.isDoubled {
                        Text("×2 = \(row.previewScore.int)")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(.orange)
                    }
                    Button { flipSign(row) } label: { Text(verbatim: "±").font(.title3) }
                        .buttonStyle(.borderless)
                        .accessibilityLabel("Change sign for \(row.name)")
                    TextField("0", text: pointsBinding(row))
                        .keyboardType(.numbersAndPunctuation)
                        .multilineTextAlignment(.trailing)
                        .font(.title3.monospacedDigit())
                        .frame(width: 72)
                        .textFieldStyle(.roundedBorder)
                        .accessibilityLabel("Points for \(row.name)")
                        .focused($focusedPlayer, equals: row.playerId)
                        .submitLabel(.next)
                        .onSubmit { focusNext(after: row.playerId, in: entry) }
                }
            }
        } footer: {
            if entry.type == .skyjo {
                Text("Tap the flag of the player who ended the round. Their points are doubled if they are positive and someone else has the same or fewer.")
            }
        }
    }

    private func pointsBinding(_ row: EntryRow) -> Binding<String> {
        Binding(
            get: { texts[row.playerId] ?? "" },
            set: { text in
                texts[row.playerId] = text
                if let points = Int32(text.replacingOccurrences(of: "−", with: "-").trimmingCharacters(in: .whitespaces)) {
                    model.dispatch(Actions.shared.setEntryPoints(playerId: row.playerId, points: points))
                } else {
                    model.dispatch(Actions.shared.clearEntryPoints(playerId: row.playerId))
                }
            }
        )
    }

    private func flipSign(_ row: EntryRow) {
        let text = texts[row.playerId] ?? ""
        let binding = pointsBinding(row)
        if text.hasPrefix("-") || text.hasPrefix("−") {
            binding.wrappedValue = String(text.dropFirst())
        } else {
            binding.wrappedValue = "-" + text
        }
    }

    private func focusNext(after playerId: String, in entry: RoundEntryModel) {
        let ids = entry.rows.map(\.playerId)
        guard let index = ids.firstIndex(of: playerId) else { return }
        focusedPlayer = index + 1 < ids.count ? ids[index + 1] : nil
    }

    // MARK: Wizard

    @ViewBuilder
    private func wizardSections(_ entry: RoundEntryModel) -> some View {
        Section {
            ForEach(entry.rows, id: \.playerId) { row in
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Text(row.name).font(.headline)
                        if row.isDealer {
                            Label("Dealer", systemImage: "suit.spade.fill")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        } else if row.isFirstBidder {
                            Label("Bids first", systemImage: "1.circle")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                        if row.hasPreview {
                            Text(signed(row.previewScore))
                                .font(.headline.monospacedDigit())
                                .foregroundStyle(row.previewScore >= 0 ? .green : .red)
                        }
                    }
                    if entry.phase == .tricks {
                        HStack(spacing: 4) {
                            Text("Bid")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                            Picker("Bid", selection: bidBinding(row)) {
                                ForEach(0...entry.cards.int, id: \.self) { value in
                                    Text(verbatim: "\(value)").tag(value)
                                }
                            }
                            .pickerStyle(.menu)
                            .labelsHidden()
                            Text("· Tricks won:")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                    }
                    NumberChips(
                        selected: row.hasValue ? row.value.int : nil,
                        range: row.minValue.int...row.maxValue.int,
                        tint: entry.type.tint
                    ) { value in
                        if entry.phase == .bids {
                            model.dispatch(Actions.shared.setEntryBid(playerId: row.playerId, bid: Int32(value)))
                        } else {
                            model.dispatch(Actions.shared.setEntryTricks(playerId: row.playerId, tricks: Int32(value)))
                        }
                    }
                }
                .padding(.vertical, 4)
            }
        } header: {
            Text("\(entry.cards.int) cards per player")
        } footer: {
            wizardSummary(entry)
        }
    }

    private func bidBinding(_ row: EntryRow) -> Binding<Int> {
        Binding(
            get: { row.bid.int },
            set: { model.dispatch(Actions.shared.setEntryBid(playerId: row.playerId, bid: Int32($0))) }
        )
    }

    @ViewBuilder
    private func wizardSummary(_ entry: RoundEntryModel) -> some View {
        let cards = entry.cards.int
        if entry.phase == .bids {
            let bids = entry.bidSum.int
            if bids > cards {
                Text("Bids: \(bids) of \(cards) · overbid by \(bids - cards)")
            } else if bids < cards {
                Text("Bids: \(bids) of \(cards) · underbid by \(cards - bids)")
            } else {
                Text("Bids: \(bids) of \(cards) · someone will miss their bid")
            }
        } else {
            Text("Tricks entered: \(entry.trickSum.int) of \(cards)")
                .foregroundStyle(entry.needsMismatchConfirmation ? .red : .secondary)
        }
    }
}

/// Tap targets for small numbers (bids and tricks), scrolling when a round has many cards.
struct NumberChips: View {
    let selected: Int?
    let range: ClosedRange<Int>
    let tint: Color
    let onSelect: (Int) -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(Array(range), id: \.self) { value in
                    let isSelected = value == selected
                    Button { onSelect(value) } label: {
                        Text(verbatim: "\(value)")
                            .font(.body.weight(.semibold).monospacedDigit())
                            .frame(width: 38, height: 38)
                            .background(isSelected ? tint : Color(.tertiarySystemFill), in: Circle())
                            .foregroundStyle(isSelected ? .white : .primary)
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(isSelected ? .isSelected : [])
                }
            }
            .padding(.vertical, 2)
        }
    }
}
