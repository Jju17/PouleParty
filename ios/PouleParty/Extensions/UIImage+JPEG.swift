import UIKit

extension UIImage {
    /// Resize + compress a captured photo for upload. Defaults to 900 px
    /// on the longest side at 0.6 quality — produces ~70-150 KB JPEGs
    /// instead of the 200-500 KB the old 1200 px / 0.8 settings emitted,
    /// which cuts validator-queue thumbnail load by 3-5×. Validators only
    /// need enough resolution to confirm "yes the challenge was done";
    /// hi-res isn't useful and dominates Firebase Storage bandwidth.
    func jpegDataResized(maxDimension: CGFloat = 900, quality: CGFloat = 0.6) -> Data? {
        let largest = max(size.width, size.height)
        let scale = largest > maxDimension ? maxDimension / largest : 1.0
        let target = CGSize(width: size.width * scale, height: size.height * scale)
        let format = UIGraphicsImageRendererFormat.default()
        // Skip the 2-3× pixel scaling Apple does by default — we already
        // baked the target size into `target`, and a "Retina"-scaled
        // canvas just inflates the encoded bytes for the same visual.
        format.scale = 1
        format.opaque = true
        let renderer = UIGraphicsImageRenderer(size: target, format: format)
        let resized = renderer.image { _ in
            draw(in: CGRect(origin: .zero, size: target))
        }
        return resized.jpegData(compressionQuality: quality)
    }
}
