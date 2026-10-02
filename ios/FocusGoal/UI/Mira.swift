import SwiftUI

enum MiraMood { case happy, focused, stern, celebrate }

/// Mira, FocusGoal's cartoon focus buddy, drawn in code so she scales crisply and can blink, bob and talk.
struct MiraView: View {
    var mood: MiraMood = .happy
    var talking = false

    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 30)) { timeline in
            let t = timeline.date.timeIntervalSinceReferenceDate
            let blink = t.truncatingRemainder(dividingBy: 4.3) < 0.13
            let bob = (sin(t * 1.75) + 1) / 2
            let mouth = talking ? abs(sin(t * 18)) : 0
            Canvas { ctx, size in
                MiraDrawing.draw(in: &ctx, size: size, mood: mood, blink: blink, mouthOpen: mouth, bob: bob)
            }
        }
        .aspectRatio(1, contentMode: .fit)
        .accessibilityLabel("Mira")
    }
}

/// Glass speech bubble.
struct SpeechBubble: View {
    let text: String
    var body: some View {
        Text(text)
            .font(.system(size: 15))
            .foregroundStyle(Palette.text)
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .modifier(Glass(shape: UnevenRoundedRectangle(topLeadingRadius: 22, bottomLeadingRadius: 6, bottomTrailingRadius: 22, topTrailingRadius: 22), tint: .white.opacity(0.1)))
            .animation(.easeInOut, value: text)
    }
}

private enum MiraDrawing {
    static let hair = Color(hex: 0x2D2150)
    static let hairShine = Color(hex: 0x5A4796)
    static let skin = Color(hex: 0xFFE4D6)
    static let skinShade = Color(hex: 0xF4C4AE)
    static let blush = Color(hex: 0xFF8FB1)
    static let eyeDark = Color(hex: 0x241A42)
    static let mouthColor = Color(hex: 0x9A3D63)

    static func draw(in ctx: inout GraphicsContext, size: CGSize, mood: MiraMood, blink: Bool, mouthOpen: Double, bob: Double) {
        let w = min(size.width, size.height)
        let ox = (size.width - w) / 2
        let oy = (size.height - w) / 2 + (bob - 0.5) * 0.03 * w
        func p(_ x: Double, _ y: Double) -> CGPoint { CGPoint(x: ox + x * w, y: oy + y * w) }
        func r(_ x: Double, _ y: Double, _ rw: Double, _ rh: Double) -> CGRect { CGRect(x: ox + x * w, y: oy + y * w, width: rw * w, height: rh * w) }
        /// Elliptical arc inside [rect], angles in degrees clockwise from 3 o'clock (like Android's drawArc).
        func arc(_ rect: CGRect, _ start: Double, _ sweep: Double, closeToCenter: Bool = false) -> Path {
            var unit = Path()
            if closeToCenter { unit.move(to: .zero) }
            unit.addArc(center: .zero, radius: 1, startAngle: .degrees(start), endAngle: .degrees(start + sweep), clockwise: false)
            if closeToCenter { unit.closeSubpath() }
            return unit.applying(CGAffineTransform(translationX: rect.midX, y: rect.midY).scaledBy(x: rect.width / 2, y: rect.height / 2))
        }
        func stroke(_ path: Path, _ color: Color, _ width: Double) {
            ctx.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: width * w, lineCap: .round))
        }
        func line(_ a: CGPoint, _ b: CGPoint, _ color: Color, _ width: Double) {
            var path = Path(); path.move(to: a); path.addLine(to: b)
            stroke(path, color, width)
        }

        ctx.clip(to: Path(CGRect(origin: .zero, size: size)))

        // ---- Back hair ----
        var back = Path()
        back.move(to: p(0.5, 0.12))
        back.addCurve(to: p(0.14, 0.62), control1: p(0.16, 0.12), control2: p(0.11, 0.45))
        back.addLine(to: p(0.13, 0.86))
        back.addQuadCurve(to: p(0.31, 0.84), control: p(0.22, 0.92))
        back.addLine(to: p(0.69, 0.84))
        back.addQuadCurve(to: p(0.87, 0.86), control: p(0.78, 0.92))
        back.addLine(to: p(0.86, 0.62))
        back.addCurve(to: p(0.5, 0.12), control1: p(0.89, 0.45), control2: p(0.84, 0.12))
        back.closeSubpath()
        ctx.fill(back, with: .color(hair))

        // ---- Neck + hoodie ----
        ctx.fill(Path(r(0.45, 0.68, 0.1, 0.14)), with: .color(skinShade))
        ctx.fill(
            Path(roundedRect: r(0.22, 0.77, 0.56, 0.4), cornerRadius: 0.17 * w),
            with: .linearGradient(Gradient(colors: [Palette.accentLight, Palette.accent, Palette.accentDeep]), startPoint: p(0.5, 0.76), endPoint: p(0.5, 1.1))
        )
        var collar = Path()
        collar.move(to: p(0.42, 0.77)); collar.addQuadCurve(to: p(0.58, 0.77), control: p(0.5, 0.86)); collar.closeSubpath()
        ctx.fill(collar, with: .color(skinShade))
        line(p(0.45, 0.82), p(0.44, 0.92), .white.opacity(0.85), 0.012)
        line(p(0.55, 0.82), p(0.56, 0.92), .white.opacity(0.85), 0.012)

        // ---- Ears + face ----
        ctx.fill(Path(ellipseIn: r(0.205, 0.47, 0.07, 0.1)), with: .color(skin))
        ctx.fill(Path(ellipseIn: r(0.725, 0.47, 0.07, 0.1)), with: .color(skin))
        ctx.fill(Path(ellipseIn: r(0.24, 0.25, 0.52, 0.5)), with: .color(skin))

        // ---- Bangs + side locks ----
        var bangs = Path()
        bangs.move(to: p(0.22, 0.5))
        bangs.addCurve(to: p(0.52, 0.12), control1: p(0.18, 0.2), control2: p(0.4, 0.11))
        bangs.addCurve(to: p(0.79, 0.5), control1: p(0.72, 0.12), control2: p(0.85, 0.26))
        bangs.addQuadCurve(to: p(0.67, 0.34), control: p(0.75, 0.37))
        bangs.addQuadCurve(to: p(0.6, 0.42), control: p(0.65, 0.4))
        bangs.addQuadCurve(to: p(0.5, 0.32), control: p(0.58, 0.34))
        bangs.addQuadCurve(to: p(0.41, 0.41), control: p(0.46, 0.39))
        bangs.addQuadCurve(to: p(0.33, 0.34), control: p(0.4, 0.33))
        bangs.addQuadCurve(to: p(0.22, 0.5), control: p(0.26, 0.38))
        bangs.closeSubpath()
        ctx.fill(bangs, with: .color(hair))

        var leftLock = Path()
        leftLock.move(to: p(0.235, 0.38)); leftLock.addQuadCurve(to: p(0.22, 0.72), control: p(0.19, 0.56))
        leftLock.addQuadCurve(to: p(0.285, 0.44), control: p(0.27, 0.62)); leftLock.closeSubpath()
        ctx.fill(leftLock, with: .color(hair))
        var rightLock = Path()
        rightLock.move(to: p(0.765, 0.38)); rightLock.addQuadCurve(to: p(0.78, 0.72), control: p(0.81, 0.56))
        rightLock.addQuadCurve(to: p(0.715, 0.44), control: p(0.73, 0.62)); rightLock.closeSubpath()
        ctx.fill(rightLock, with: .color(hair))
        stroke(arc(r(0.3, 0.17, 0.3, 0.2), 200, 70), hairShine, 0.022)

        // ---- Accessory: headphones while focusing, a hair clip otherwise ----
        if mood == .focused {
            stroke(arc(r(0.17, 0.08, 0.66, 0.72), 180, 180), Palette.accentLight, 0.04)
            ctx.fill(Path(roundedRect: r(0.14, 0.41, 0.1, 0.18), cornerRadius: 0.05 * w), with: .color(Palette.accent))
            ctx.fill(Path(roundedRect: r(0.76, 0.41, 0.1, 0.18), cornerRadius: 0.05 * w), with: .color(Palette.accent))
        } else {
            ctx.fill(Path(ellipseIn: CGRect(origin: p(0.652, 0.212), size: CGSize(width: 0.076 * w, height: 0.076 * w))), with: .color(Palette.accentLight))
            ctx.fill(Path(ellipseIn: CGRect(origin: p(0.676, 0.236), size: CGSize(width: 0.028 * w, height: 0.028 * w))), with: .color(.white))
        }

        // ---- Eyes ----
        let closedHappy = (mood == .happy && blink) || mood == .celebrate
        for (i, c) in [p(0.39, 0.54), p(0.61, 0.54)].enumerated() {
            if closedHappy {
                stroke(arc(CGRect(x: c.x - 0.042 * w, y: c.y - 0.02 * w, width: 0.084 * w, height: 0.07 * w), 200, 140), eyeDark, 0.016)
            } else if blink {
                stroke(arc(CGRect(x: c.x - 0.04 * w, y: c.y - 0.045 * w, width: 0.08 * w, height: 0.05 * w), 20, 140), eyeDark, 0.014)
            } else {
                let h = mood == .stern ? 0.09 : 0.12
                ctx.fill(Path(ellipseIn: CGRect(x: c.x - 0.045 * w, y: c.y - h / 2 * w, width: 0.09 * w, height: h * w)), with: .color(eyeDark))
                ctx.fill(Path(ellipseIn: CGRect(x: c.x - 0.033 * w, y: c.y, width: 0.066 * w, height: (h / 2 - 0.008) * w)), with: .color(Palette.accent.opacity(0.85)))
                ctx.fill(Path(ellipseIn: CGRect(x: c.x - 0.031 * w, y: c.y - 0.041 * w, width: 0.034 * w, height: 0.034 * w)), with: .color(.white))
                ctx.fill(Path(ellipseIn: CGRect(x: c.x + 0.008 * w, y: c.y + 0.012 * w, width: 0.016 * w, height: 0.016 * w)), with: .color(.white.opacity(0.8)))
                let dir: Double = i == 0 ? -1 : 1
                line(CGPoint(x: c.x + dir * 0.04 * w, y: c.y - 0.035 * w), CGPoint(x: c.x + dir * 0.062 * w, y: c.y - 0.05 * w), eyeDark, 0.012)
            }
        }

        // ---- Brows ----
        switch mood {
        case .stern:
            line(p(0.335, 0.435), p(0.43, 0.465), hair, 0.012)
            line(p(0.665, 0.435), p(0.57, 0.465), hair, 0.012)
        case .focused:
            line(p(0.345, 0.455), p(0.43, 0.45), hair, 0.012)
            line(p(0.655, 0.455), p(0.57, 0.45), hair, 0.012)
        default:
            break
        }

        // ---- Blush + nose ----
        ctx.fill(Path(ellipseIn: r(0.29, 0.6, 0.09, 0.045)), with: .color(blush.opacity(0.45)))
        ctx.fill(Path(ellipseIn: r(0.62, 0.6, 0.09, 0.045)), with: .color(blush.opacity(0.45)))
        ctx.fill(Path(ellipseIn: CGRect(origin: p(0.492, 0.592), size: CGSize(width: 0.016 * w, height: 0.016 * w))), with: .color(skinShade))

        // ---- Mouth ----
        let m = p(0.5, 0.655)
        if mouthOpen > 0 {
            ctx.fill(Path(ellipseIn: CGRect(x: m.x - 0.03 * w, y: m.y - 0.012 * w, width: 0.06 * w, height: (0.02 + 0.04 * mouthOpen) * w)), with: .color(mouthColor))
        } else {
            switch mood {
            case .celebrate:
                ctx.fill(arc(CGRect(x: m.x - 0.045 * w, y: m.y - 0.035 * w, width: 0.09 * w, height: 0.07 * w), 0, 180, closeToCenter: true), with: .color(mouthColor))
            case .stern:
                stroke(arc(CGRect(x: m.x - 0.03 * w, y: m.y, width: 0.06 * w, height: 0.04 * w), 205, 130), mouthColor, 0.012)
            case .focused:
                stroke(arc(CGRect(x: m.x - 0.025 * w, y: m.y - 0.025 * w, width: 0.05 * w, height: 0.03 * w), 30, 120), mouthColor, 0.012)
            case .happy:
                stroke(arc(CGRect(x: m.x - 0.04 * w, y: m.y - 0.035 * w, width: 0.08 * w, height: 0.05 * w), 20, 140), mouthColor, 0.013)
            }
        }
    }
}
