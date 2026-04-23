package app.paperkeep.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Paperkeep shape tokens — per design spec §7.
 *
 *  Cards  → LargeRoundedCornerShape (20dp)
 *  FABs   → squircle approximation (50% radius = fully rounded)
 *  Sheets → top corners 28dp, bottom 0dp (standard bottom-sheet)
 *  Chips  → 8dp
 */
val PaperkeepShapes = Shapes(
    // ExtraSmall: tags, badges
    extraSmall = RoundedCornerShape(4.dp),
    // Small: chips, text fields
    small = RoundedCornerShape(8.dp),
    // Medium: dialogs, small cards
    medium = RoundedCornerShape(12.dp),
    // Large: document cards (spec: 20dp)
    large = RoundedCornerShape(20.dp),
    // ExtraLarge: FABs, bottom sheets top edge (spec: squircle ≈ 40% → 28dp on 70dp FAB)
    extraLarge = RoundedCornerShape(28.dp),
)

// Convenience aliases for use outside MaterialTheme
val CardShape = RoundedCornerShape(20.dp)
val FabShape  = RoundedCornerShape(percent = 50)       // squircle / pill
val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
val ChipShape = RoundedCornerShape(8.dp)
