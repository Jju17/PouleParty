
import Foundation

/// Power-up types that have no effect in `stayInTheZone` because they rely
/// on the chicken broadcasting its position.
private let positionDependentPowerUps: Set<PowerUp.PowerUpType> = [
    .invisibility,
    .decoy,
    .jammer,
]

func availablePowerUpTypes(for mode: Game.GameMode) -> [PowerUp.PowerUpType] {
    PowerUp.PowerUpType.allCases.filter { type in
        switch mode {
        case .followTheChicken:
            return true
        case .stayInTheZone:
            return !positionDependentPowerUps.contains(type)
        }
    }
}
