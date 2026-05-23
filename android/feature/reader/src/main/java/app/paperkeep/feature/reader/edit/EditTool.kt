package app.paperkeep.feature.reader.edit

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.PhotoFilter
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The 10 sub-tools surfaced in the Edit toolbar (Feature 1).
 *
 * `implemented = true` means tapping the chip runs the real flow.
 * `implemented = false` opens the "coming soon" sheet so the toolbar stays
 * honest about what works today.
 */
enum class EditTool(
    val label: String,
    val icon: ImageVector,
    val implemented: Boolean,
    val tag: String,
) {
    REORDER_PAGES("Reorder", Icons.Filled.SortByAlpha, true, "edit_tool_reorder"),
    CROP("Crop", Icons.Filled.Crop, true, "edit_tool_crop"),
    FILTER("Filter", Icons.Filled.PhotoFilter, true, "edit_tool_filter"),
    RETAKE("Retake", Icons.Filled.Replay, true, "edit_tool_retake"),
    PAGE_TITLE("Page Title", Icons.Filled.DriveFileRenameOutline, true, "edit_tool_page_title"),
    CS_PDF("CS PDF", Icons.Filled.PictureAsPdf, false, "edit_tool_cs_pdf"),
    EDIT_TEXT("Edit Text", Icons.Filled.TextFormat, false, "edit_tool_edit_text"),
    SMART_ERASE("Smart Erase", Icons.Filled.AutoFixHigh, false, "edit_tool_smart_erase"),
    ADD_TEXT("Add Text", Icons.Filled.TextFields, false, "edit_tool_add_text"),
    BRUSH("Brush", Icons.Filled.Brush, false, "edit_tool_brush"),
}
