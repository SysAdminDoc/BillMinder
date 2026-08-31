package com.sysadmindoc.billminder.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sysadmindoc.billminder.ui.theme.CatBlue
import com.sysadmindoc.billminder.ui.theme.CatCrust
import com.sysadmindoc.billminder.ui.theme.CatDivider
import com.sysadmindoc.billminder.ui.theme.CatSurface0
import com.sysadmindoc.billminder.ui.theme.CatSurface2
import com.sysadmindoc.billminder.ui.theme.CatSurfaceRaised
import com.sysadmindoc.billminder.ui.theme.CatSubtext0
import com.sysadmindoc.billminder.ui.theme.CatText

@Composable
fun SectionHeading(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = CatBlue,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.1.sp,
            color = color,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke(this)
    }
}

@Composable
fun GroupedSurface(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp),
    cornerRadius: Dp = 12.dp,
    color: Color = CatSurfaceRaised,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(cornerRadius),
        color = color,
        border = BorderStroke(1.dp, CatDivider)
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun GroupDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, color = CatDivider)
}

@Composable
fun IconWell(
    icon: ImageVector,
    contentDescription: String?,
    tint: Color = CatBlue,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.size(42.dp),
        shape = RoundedCornerShape(8.dp),
        color = CatSurface0.copy(alpha = 0.62f),
        border = BorderStroke(1.dp, CatDivider)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(21.dp)
            )
        }
    }
}

@Composable
fun SquareToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = onCheckedChange
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.size(26.dp),
            shape = RoundedCornerShape(4.dp),
            color = if (checked) CatBlue else Color.Transparent,
            border = BorderStroke(1.dp, if (checked) CatBlue else CatSurface2)
        ) {
            if (checked) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = CatCrust,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsStyleRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color = CatBlue,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 2.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconWell(icon = icon, contentDescription = null, tint = tint)
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = CatText
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = CatSubtext0,
                maxLines = 2
            )
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}
