package com.example.dukanhisabnext.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.dukanhisabnext.ui.theme.AppTheme

/** One tab of the [BottomBar]. */
data class BottomBarItem(val label: String, @DrawableRes val icon: Int, val selected: Boolean, val onClick: () -> Unit)

/**
 * The bottom bar: the surface colour, a 1dp line on top, and the selected tab shown as a soft green
 * pill behind its icon, with the label in SemiBold. Every tab is at least 56dp high.
 */
@Composable
fun BottomBar(items: List<BottomBarItem>, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = AppTheme.colors.border, thickness = 1.dp)
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                items.forEach { item ->
                    val tint = if (item.selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp)
                            .clip(MaterialTheme.shapes.small)
                            .selectable(selected = item.selected, role = Role.Tab, onClick = item.onClick),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(56.dp)
                                .height(32.dp)
                                .clip(CircleShape)
                                .background(if (item.selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(painterResource(item.icon), contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                        }
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (item.selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = tint,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
