import SwiftUI

extension Color {
    init(hex: UInt32, opacity: Double = 1) {
        self.init(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: opacity
        )
    }
}

/// One accent colour on a deep night background; everything else is white at different opacities.
enum Palette {
    static let bgTop = Color(hex: 0x0E0B1F)
    static let bgBottom = Color(hex: 0x1B1446)
    static let accent = Color(hex: 0x8B7CFF)
    static let accentLight = Color(hex: 0xB9AEFF)
    static let accentDeep = Color(hex: 0x5B45E0)
    static let text = Color.white
    static let textDim = Color.white.opacity(0.68)
    static let textFaint = Color.white.opacity(0.42)
    static let danger = Color(hex: 0xFF7A9A)
    static let success = Color(hex: 0x4ADE80)
    static let accentGradient = LinearGradient(colors: [accentLight, accent, accentDeep], startPoint: .topLeading, endPoint: .bottomTrailing)
}

/// Night gradient with soft glowing blobs — what the glass panels sit on.
struct GlowBackground: View {
    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width
            ZStack {
                LinearGradient(colors: [Palette.bgTop, Palette.bgBottom], startPoint: .top, endPoint: .bottom)
                Circle().fill(Palette.accent.opacity(0.45)).frame(width: w * 1.1).blur(radius: 90)
                    .position(x: w * 0.1, y: geo.size.height * 0.08)
                Circle().fill(Color(hex: 0xFF6FB5).opacity(0.18)).frame(width: w).blur(radius: 90)
                    .position(x: w, y: geo.size.height * 0.4)
                Circle().fill(Color(hex: 0x4F7BFF).opacity(0.22)).frame(width: w * 1.2).blur(radius: 100)
                    .position(x: w * 0.15, y: geo.size.height * 0.95)
            }
        }
        .ignoresSafeArea()
    }
}

/// Frosted glass: real blur material, a soft white tint and a hairline gradient border.
struct Glass<S: Shape>: ViewModifier {
    var shape: S
    var tint: Color = .white.opacity(0.06)

    func body(content: Content) -> some View {
        content
            .background(shape.fill(.ultraThinMaterial))
            .background(shape.fill(tint))
            .overlay(shape.stroke(
                LinearGradient(colors: [.white.opacity(0.28), .white.opacity(0.04)], startPoint: .topLeading, endPoint: .bottomTrailing),
                lineWidth: 1
            ))
            .clipShape(shape)
    }
}

extension View {
    func glass(radius: CGFloat = 24, tint: Color = .white.opacity(0.06)) -> some View {
        modifier(Glass(shape: RoundedRectangle(cornerRadius: radius, style: .continuous), tint: tint))
    }

    func glassCapsule(tint: Color = .white.opacity(0.06)) -> some View {
        modifier(Glass(shape: Capsule(), tint: tint))
    }
}

struct GlassCard<Content: View>: View {
    var padding: CGFloat = 18
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 0) { content }
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .glass()
    }
}

struct PillButton: View {
    let title: String
    var systemImage: String? = nil
    var filled = true
    var enabled = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if let systemImage { Image(systemName: systemImage) }
                Text(title).font(.system(size: 16, weight: .semibold))
            }
            .foregroundStyle(enabled ? Color.white : Palette.textFaint)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .background {
                if filled && enabled {
                    Capsule().fill(Palette.accentGradient)
                        .overlay(Capsule().strokeBorder(.white.opacity(0.25), lineWidth: 1))
                }
            }
            .modifier(Glass(shape: Capsule(), tint: filled && enabled ? .clear : .white.opacity(0.1)))
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}

struct SectionLabel: View {
    let text: String
    var body: some View {
        Text(text.uppercased())
            .font(.system(size: 12, weight: .medium))
            .kerning(0.6)
            .foregroundStyle(Palette.textFaint)
    }
}

/// Circular progress ring with an accent sweep and a glowing head.
struct TimerRing<Content: View>: View {
    var progress: Double
    var lineWidth: CGFloat = 14
    @ViewBuilder var content: Content

    var body: some View {
        ZStack {
            Circle().stroke(.white.opacity(0.08), lineWidth: lineWidth)
            Circle()
                .trim(from: 0, to: max(0.001, min(1, progress)))
                .stroke(
                    AngularGradient(colors: [Palette.accentDeep, Palette.accent, Palette.accentLight, Palette.accentDeep], center: .center),
                    style: StrokeStyle(lineWidth: lineWidth, lineCap: .round)
                )
                .rotationEffect(.degrees(-90))
                .shadow(color: Palette.accent.opacity(0.6), radius: 10)
            content
        }
        .padding(lineWidth / 2 + 4)
        .animation(.linear(duration: 0.6), value: progress)
    }
}

func formatCountdown(_ seconds: TimeInterval) -> String {
    let total = Int(seconds.rounded(.up))
    let h = total / 3600, m = (total % 3600) / 60, s = total % 60
    return h > 0 ? String(format: "%d:%02d:%02d", h, m, s) : String(format: "%02d:%02d", m, s)
}

func formatClock(_ date: Date) -> String {
    date.formatted(date: .omitted, time: .shortened)
}
