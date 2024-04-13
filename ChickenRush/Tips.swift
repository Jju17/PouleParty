//
//  Tips
//  ChickenRush
//
//  Created by Julien Rahier on 04/04/2024.
//

import TipKit

struct SelectChickenTip: Tip {
    var title: Text {
        Text("Select Chicken")
    }

    var message: Text? {
        Text("If you are the person that should be found.")
    }
}

struct SelectHunterTip: Tip {
    var title: Text {
        Text("Select Hunter")
    }

    var message: Text? {
        Text("If you are the person that should find the others.")
    }
}
