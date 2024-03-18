//
//  UUID+Utils.swift
//  ChickenRush
//
//  Created by Julien Rahier on 18/03/2024.
//

import Foundation

extension UUID {
    var shortString: String {
        self.uuidString.split(separator: "-").first?.base ?? self.uuidString
    }
}
